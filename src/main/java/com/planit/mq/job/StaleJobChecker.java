package com.planit.mq.job;

import com.planit.mq.config.RabbitMqConfig;
import com.planit.photomission.repository.MissionGenerationJobRepository;
import com.planit.schedule.repository.ScheduleGenerationJobRepository;
import io.sentry.Sentry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 메시지가 유실되거나 지연돼 기준 시간 안에 결과가 오지 않은 작업을 3분마다 실패로 끝낸다.
 * 일정 생성은 접수 후 28분, 포토미션 생성은 지금 이전의 가장 최근 06:00이 기준이다.
 * 사용자는 늦어도 기준 시간에 3분을 더한 시각까지 실패 알림을 받는다.
 * 포토미션 판정은 판정 처리 쪽에서 따로 확인한다.
 */
@Component
public class StaleJobChecker {

    /** 일정 생성 요청 메시지 deadlineAt(접수 + 28분)과 같다. */
    static final Duration SCHEDULE_GENERATION_TIMEOUT = Duration.ofMinutes(28);
    /** 포토미션 생성 요청 메시지 deadlineAt(당일 06:00)과 같다. */
    static final LocalTime MISSION_GENERATION_DEADLINE = LocalTime.of(6, 0);

    private static final Logger log = LoggerFactory.getLogger(StaleJobChecker.class);

    private final ScheduleGenerationJobRepository scheduleJobRepository;
    private final MissionGenerationJobRepository missionJobRepository;
    private final StaleJobFailureService failureService;
    private final Clock clock;

    public StaleJobChecker(
            ScheduleGenerationJobRepository scheduleJobRepository,
            MissionGenerationJobRepository missionJobRepository,
            StaleJobFailureService failureService,
            @Qualifier(RabbitMqConfig.MQ_CLOCK) Clock clock
    ) {
        this.scheduleJobRepository = scheduleJobRepository;
        this.missionJobRepository = missionJobRepository;
        this.failureService = failureService;
        this.clock = clock;
    }

    /** 다음 실행(3분 뒤) 전에 락이 풀리도록 179초로 둔다. */
    @Scheduled(cron = "0 */3 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "staleJobChecker", lockAtMostFor = "PT179S")
    public void check() {
        LocalDateTime now = LocalDateTime.now(clock);
        failAll(AiJobType.SCHEDULE_GENERATE,
                scheduleJobRepository.findQueuedIdsCreatedBefore(now.minus(SCHEDULE_GENERATION_TIMEOUT)));
        failAll(AiJobType.PHOTO_MISSION_GENERATE,
                missionJobRepository.findQueuedIdsAttemptedBefore(missionGenerationCutoff(now)));
    }

    /** 지금 이전의 가장 최근 06:00. 06:00 전이면 전날 06:00이다. */
    static LocalDateTime missionGenerationCutoff(LocalDateTime now) {
        LocalDateTime todayDeadline = now.toLocalDate().atTime(MISSION_GENERATION_DEADLINE);
        return now.isBefore(todayDeadline) ? todayDeadline.minusDays(1) : todayDeadline;
    }

    /** 건마다 따로 처리해 한 작업의 오류가 다른 작업의 실패 처리를 막지 않게 한다. */
    private void failAll(AiJobType jobType, List<Long> jobIds) {
        int failedCount = 0;
        for (Long jobId : jobIds) {
            try {
                if (failureService.fail(jobType, jobId)) {
                    failedCount++;
                }
            } catch (RuntimeException e) {
                log.error("Stale 작업 실패 처리 중 오류 jobType={}, jobId={}", jobType, jobId, e);
                Sentry.captureException(e);
            }
        }
        if (failedCount > 0) {
            log.warn("기준 시간 안에 결과가 오지 않은 작업을 실패 처리함 jobType={}, count={}", jobType, failedCount);
        }
    }
}
