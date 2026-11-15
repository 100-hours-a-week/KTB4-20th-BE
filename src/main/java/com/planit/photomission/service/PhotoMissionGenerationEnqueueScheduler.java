package com.planit.photomission.service;

import com.planit.mq.intake.AiJobIntakeGuard;
import com.planit.mq.job.AiJobType;
import com.planit.photomission.domain.MissionGenerationJob;
import com.planit.repository.TripRepository;
import io.sentry.Sentry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 전환 플래그가 켜져 있으면 매일 00:00에 그날 시작하는 여행의 포토미션 생성을 메시지 큐로 접수한다.
 * 대상은 기존 동기 방식과 같은 조회로 고르고, 여행마다 따로 접수해 한 여행의 오류가 다른 여행을 막지 않게 한다.
 * 미션 공개 시각 00:30은 바뀌지 않는다.
 * 기존 5분 주기 스케줄러와 락 이름을 나눈다. 같은 이름이면 00:00에 기존 스케줄러가 락을 먼저 잡았을 때 이 스케줄러가 그날 접수를 건너뛴다.
 * 두 스케줄러는 플래그로 한쪽만 일하므로 락을 나눠도 같은 여행을 두 번 처리하지 않는다.
 */
@Component
public class PhotoMissionGenerationEnqueueScheduler {

    private static final Logger log = LoggerFactory.getLogger(PhotoMissionGenerationEnqueueScheduler.class);

    private final TripRepository tripRepository;
    private final PhotoMissionGenerationEnqueueService enqueueService;
    private final AiJobIntakeGuard intakeGuard;
    private final Clock clock;

    public PhotoMissionGenerationEnqueueScheduler(
            TripRepository tripRepository,
            PhotoMissionGenerationEnqueueService enqueueService,
            AiJobIntakeGuard intakeGuard,
            @Qualifier("photoMissionClock") Clock photoMissionClock
    ) {
        this.tripRepository = tripRepository;
        this.enqueueService = enqueueService;
        this.intakeGuard = intakeGuard;
        this.clock = photoMissionClock;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "photoMissionGenerationEnqueue", lockAtMostFor = "PT5M")
    public void enqueue() {
        if (!intakeGuard.isAsync(AiJobType.PHOTO_MISSION_GENERATE)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> tripIds = tripRepository.findPhotoMissionGenerationTargetIds(
                now.toLocalDate(),
                MissionGenerationJob.staleBefore(now)
        );
        int enqueuedCount = 0;
        for (Long tripId : tripIds) {
            try {
                enqueueService.enqueue(tripId, now);
                enqueuedCount++;
            } catch (RuntimeException e) {
                log.error("포토미션 생성 접수 실패 tripId={}", tripId, e);
                Sentry.captureException(e);
            }
        }
        log.info("포토미션 생성 접수 date={}, targets={}, enqueued={}", now.toLocalDate(), tripIds.size(), enqueuedCount);
    }
}
