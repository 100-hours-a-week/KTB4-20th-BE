package com.planit.notification.precheck;

import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
public class TripRemindNotificationScheduler {

    static final String LOCK_NAME = "tripRemindNotificationWorkflow";
    static final String SEOUL_ZONE = "Asia/Seoul";

    private final TripRemindNotificationWorkflow workflow;
    private final TripRemindNotificationObservability observability;
    private final Clock clock;

    public TripRemindNotificationScheduler(
            TripRemindNotificationWorkflow workflow,
            TripRemindNotificationObservability observability,
            @Qualifier("tripRemindClock") Clock clock
    ) {
        this.workflow = workflow;
        this.observability = observability;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 10 * * *", zone = SEOUL_ZONE)
    @SchedulerLock(
            name = LOCK_NAME,
            lockAtMostFor = "PT5M",
            lockAtLeastFor = "PT1M"
    )
    public void runPrimary() {
        executeWorkflow("primary", false);
    }

    @Scheduled(cron = "0 30 10 * * *", zone = SEOUL_ZONE)
    @SchedulerLock(
            name = LOCK_NAME,
            lockAtMostFor = "PT5M",
            lockAtLeastFor = "PT1M"
    )
    public void runRecovery() {
        executeWorkflow("recovery", true);
    }

    private void executeWorkflow(String trigger, boolean finalRecovery) {
        LockAssert.assertLocked();
        LocalDate targetDate = LocalDate.now(clock).plusDays(1);
        long startedAt = System.nanoTime();
        String outcome = "success";
        try {
            workflow.execute(targetDate);
        } catch (RuntimeException exception) {
            outcome = "failure";
            if (finalRecovery) {
                observability.recordFinalRecoveryFailure(
                        targetDate,
                        exception
                );
            }
            throw exception;
        } finally {
            observability.recordExecution(
                    trigger,
                    outcome,
                    java.time.Duration.ofNanos(
                            System.nanoTime() - startedAt
                    )
            );
        }
    }
}
