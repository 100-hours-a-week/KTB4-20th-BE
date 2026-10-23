package com.planit.photomission.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class PhotoMissionGenerationScheduler {

    private final PhotoMissionGenerationService generationService;
    private final Clock clock;

    public PhotoMissionGenerationScheduler(
            PhotoMissionGenerationService generationService,
            @Qualifier("photoMissionClock") Clock photoMissionClock
    ) {
        this.generationService = generationService;
        this.clock = photoMissionClock;
    }

    @Scheduled(cron = "0 10 14 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "photoMissionGeneration")
    public void generate() {
        generationService.generateDueTrips(LocalDateTime.now(clock));
    }
}
