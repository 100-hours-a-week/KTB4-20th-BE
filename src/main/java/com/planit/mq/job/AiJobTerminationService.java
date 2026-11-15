package com.planit.mq.job;

import com.planit.domain.Trip;
import com.planit.mq.config.RabbitMqConfig;
import com.planit.notification.aijob.ScheduleGenerationNotificationService;
import com.planit.photomission.domain.MissionGenerationStatus;
import com.planit.photomission.repository.MissionGenerationJobRepository;
import com.planit.schedule.domain.ScheduleGenerationJobStatus;
import com.planit.schedule.repository.ScheduleGenerationJobRepository;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 메시지 큐로 접수한 AI 작업을 끝낸다. Result Worker, DLQ, Stale, 발행 실패가 모두 이 서비스로 종료한다.
 * 종료는 `status = 'QUEUED'` 조건부 UPDATE로 하고, 이번 호출이 바꿨을 때만 알림을 저장한다.
 * 알림은 같은 트랜잭션에 저장하고 커밋된 뒤에 실시간으로 발행한다.
 * 포토미션 판정은 판정 처리 쪽에서 따로 끝낸다.
 */
@Service
public class AiJobTerminationService {

    /** 결과가 왔지만 대상 여행이 이미 삭제된 경우의 실패 원인 */
    public static final String TRIP_DELETED = "TRIP_DELETED";

    private static final Logger log = LoggerFactory.getLogger(AiJobTerminationService.class);

    private final ScheduleGenerationJobRepository scheduleJobRepository;
    private final MissionGenerationJobRepository missionJobRepository;
    private final ScheduleGenerationNotificationService scheduleNotificationService;
    private final Clock clock;

    public AiJobTerminationService(
            ScheduleGenerationJobRepository scheduleJobRepository,
            MissionGenerationJobRepository missionJobRepository,
            ScheduleGenerationNotificationService scheduleNotificationService,
            @Qualifier(RabbitMqConfig.MQ_CLOCK) Clock clock
    ) {
        this.scheduleJobRepository = scheduleJobRepository;
        this.missionJobRepository = missionJobRepository;
        this.scheduleNotificationService = scheduleNotificationService;
        this.clock = clock;
    }

    /**
     * 일정 생성 작업을 성공으로 끝내고 여행방 멤버에게 알린다. 여행이 삭제됐으면 `TRIP_DELETED`로 실패 처리하고 알리지 않는다.
     *
     * @return 이번 호출이 성공으로 끝냈으면 true. 호출한 쪽은 true일 때만 결과를 저장한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean succeedScheduleGeneration(Long jobId) {
        Optional<Trip> trip = scheduleTripOf(jobId);
        if (!isSucceedable(trip, () -> finishSchedule(jobId, ScheduleGenerationJobStatus.FAILED, TRIP_DELETED))
                || !finishSchedule(jobId, ScheduleGenerationJobStatus.SUCCEEDED, null)) {
            return false;
        }
        scheduleNotificationService.notifySucceeded(jobId, trip.get());
        return true;
    }

    /**
     * 일정 생성 작업을 실패로 끝내고, 여행이 남아 있으면 여행방 멤버에게 알린다.
     *
     * @param errorCode 실패 원인. 작업 테이블 `error_code`에 저장한다.
     * @return 이번 호출이 실패로 끝냈으면 true
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean failScheduleGeneration(Long jobId, String errorCode) {
        requireErrorCode(errorCode);
        Optional<Trip> trip = scheduleTripOf(jobId);
        if (trip.isEmpty() || !finishSchedule(jobId, ScheduleGenerationJobStatus.FAILED, errorCode)) {
            return false;
        }
        notifyFailedIfTripAlive(jobId, trip.get());
        return true;
    }

    /**
     * 포토미션 생성 작업을 성공으로 끝낸다. 여행이 삭제됐으면 실패로 끝낸다. 포토미션 생성은 알림이 없다.
     *
     * @return 이번 호출이 성공으로 끝냈으면 true. 호출한 쪽은 true일 때만 결과를 저장한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean succeedMissionGeneration(Long jobId) {
        Optional<Trip> trip = missionTripOf(jobId);
        return isSucceedable(trip, () -> failMissionGeneration(jobId, TRIP_DELETED))
                && finishMission(jobId, MissionGenerationStatus.SUCCEEDED);
    }

    /**
     * 포토미션 생성 작업을 실패로 끝낸다. 작업 테이블에 실패 원인 컬럼이 없어 로그로만 남긴다.
     *
     * @return 이번 호출이 실패로 끝냈으면 true
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean failMissionGeneration(Long jobId, String errorCode) {
        requireErrorCode(errorCode);
        if (!finishMission(jobId, MissionGenerationStatus.FAILED)) {
            return false;
        }
        log.warn("포토미션 생성 작업 실패 jobId={}, errorCode={}", jobId, errorCode);
        return true;
    }

    /**
     * 작업 종류에 맞게 실패로 끝낸다. 메시지 헤더의 작업 종류만 아는 DLQ, Stale, 발행 실패 경로에서 쓴다.
     *
     * @return 이번 호출이 실패로 끝냈으면 true
     * @throws IllegalArgumentException 포토미션 판정 작업이면
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean fail(AiJobType jobType, Long jobId, String errorCode) {
        return switch (jobType) {
            case SCHEDULE_GENERATE -> failScheduleGeneration(jobId, errorCode);
            case PHOTO_MISSION_GENERATE -> failMissionGeneration(jobId, errorCode);
            case PHOTO_JUDGE -> throw new IllegalArgumentException("포토미션 판정 작업은 판정 처리에서 종료합니다");
        };
    }

    private boolean finishSchedule(Long jobId, ScheduleGenerationJobStatus status, String errorCode) {
        return scheduleJobRepository.finishQueued(jobId, status, errorCode, LocalDateTime.now(clock)) == 1;
    }

    private boolean finishMission(Long jobId, MissionGenerationStatus status) {
        return missionJobRepository.finishQueued(jobId, status) == 1;
    }

    /**
     * 성공 결과를 반영할 수 있는지 확인한다. 작업이 없으면 false이고,
     * 여행이 삭제됐으면 {@code failByTripDeleted}로 작업을 실패로 끝낸 뒤 false를 돌려준다.
     */
    private static boolean isSucceedable(Optional<Trip> trip, Runnable failByTripDeleted) {
        if (trip.isEmpty()) {
            return false;
        }
        if (isDeleted(trip.get())) {
            failByTripDeleted.run();
            return false;
        }
        return true;
    }

    private void notifyFailedIfTripAlive(Long jobId, Trip trip) {
        if (!isDeleted(trip)) {
            scheduleNotificationService.notifyFailed(jobId, trip);
        }
    }

    private static void requireErrorCode(String errorCode) {
        Objects.requireNonNull(errorCode, "실패 원인은 필수입니다");
    }

    /** 없는 작업 ID의 결과는 처리할 대상이 없으므로 이미 끝난 작업처럼 무시한다. */
    private Optional<Trip> scheduleTripOf(Long jobId) {
        Optional<Trip> trip = scheduleJobRepository.findById(jobId).map(job -> loaded(job.getTrip()));
        warnIfMissing(trip, AiJobType.SCHEDULE_GENERATE, jobId);
        return trip;
    }

    private Optional<Trip> missionTripOf(Long jobId) {
        Optional<Trip> trip = missionJobRepository.findById(jobId).map(job -> loaded(job.getTrip()));
        warnIfMissing(trip, AiJobType.PHOTO_MISSION_GENERATE, jobId);
        return trip;
    }

    /**
     * 종료 UPDATE가 영속성 컨텍스트를 비우므로, 그 뒤에도 삭제 여부와 이름을 읽을 수 있게 여행을 미리 불러 둔다.
     */
    private static Trip loaded(Trip trip) {
        Hibernate.initialize(trip);
        return trip;
    }

    private static void warnIfMissing(Optional<Trip> trip, AiJobType jobType, Long jobId) {
        if (trip.isEmpty()) {
            log.warn("종료할 AI 작업이 없음 jobType={}, jobId={}", jobType, jobId);
        }
    }

    private static boolean isDeleted(Trip trip) {
        return trip.getDeletedAt() != null;
    }
}
