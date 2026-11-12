package com.planit.photomission.service;

import com.planit.mq.intake.AiJobIntakeGuard;
import com.planit.mq.job.AiJobType;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 기존 동기 방식의 포토미션 생성 스케줄러. 전환 플래그가 켜져 있으면 메시지 큐 접수 스케줄러가 대신 일하므로 바로 끝낸다.
 */
@Component
public class PhotoMissionGenerationScheduler {

    private final PhotoMissionGenerationService generationService;
    private final AiJobIntakeGuard intakeGuard;
    private final Clock clock;

    public PhotoMissionGenerationScheduler(
            PhotoMissionGenerationService generationService,
            AiJobIntakeGuard intakeGuard,
            @Qualifier("photoMissionClock") Clock photoMissionClock
    ) {
        this.generationService = generationService;
        this.intakeGuard = intakeGuard;
        this.clock = photoMissionClock;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "photoMissionGeneration")
    public void generate() {
        if (intakeGuard.isAsync(AiJobType.PHOTO_MISSION_GENERATE)) {
            return;
        }
        generationService.generateDueTrips(LocalDateTime.now(clock));
    }
}
