package com.planit.notification.precheck;

import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class TripRemindNotificationScheduler {

    static final String LOCK_NAME = "tripRemindNotificationWorkflow";
    static final String SEOUL_ZONE = "Asia/Seoul";

    private final TripRemindNotificationWorkflow workflow;
    private final Clock clock;

    @Scheduled(cron = "0 0 10 * * *", zone = SEOUL_ZONE)
    @SchedulerLock(
            name = LOCK_NAME,
            lockAtMostFor = "PT5M",
            lockAtLeastFor = "PT1M"
    )
    public void runPrimary() {
        executeWorkflow();
    }

    @Scheduled(cron = "0 30 10 * * *", zone = SEOUL_ZONE)
    @SchedulerLock(
            name = LOCK_NAME,
            lockAtMostFor = "PT5M",
            lockAtLeastFor = "PT1M"
    )
    public void runRecovery() {
        executeWorkflow();
    }

    private void executeWorkflow() {
        LockAssert.assertLocked();
        workflow.execute(LocalDate.now(clock).plusDays(1));
    }
}
