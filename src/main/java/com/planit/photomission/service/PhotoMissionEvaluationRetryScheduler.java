package com.planit.photomission.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class PhotoMissionEvaluationRetryScheduler {

    private final PhotoMissionEvaluationRetryService retryService;
    private final Clock clock;

    public PhotoMissionEvaluationRetryScheduler(
            PhotoMissionEvaluationRetryService retryService,
            @Qualifier("photoMissionEvaluationRetryClock") Clock clock
    ) {
        this.retryService = retryService;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString =
                    "${photo-mission.evaluation-retry-delay-ms:10000}"
    )
    @SchedulerLock(
            name = "photoMissionEvaluationRetry",
            lockAtMostFor = "PT5M"
    )
    public void retry() {
        retryService.retryDue(LocalDateTime.now(clock));
    }
}
