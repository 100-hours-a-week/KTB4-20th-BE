package com.planit.notification.precheck;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class TripRemindNotificationObservability {

    static final Duration LOCK_AT_MOST_FOR = Duration.ofMinutes(5);

    private final MeterRegistry meterRegistry;

    public void recordExecution(
            String trigger,
            String outcome,
            Duration duration
    ) {
        Timer.builder("planit.notification.workflow.duration")
                .description("D-1 알림 워크플로 실행시간")
                .tags("trigger", trigger, "outcome", outcome)
                .register(meterRegistry)
                .record(duration);

        if (duration.compareTo(LOCK_AT_MOST_FOR) >= 0) {
            Counter.builder(
                            "planit.notification.workflow.lock_at_most_exceeded"
                    )
                    .description("알림 워크플로의 ShedLock 최대 시간을 초과한 횟수")
                    .tag("trigger", trigger)
                    .register(meterRegistry)
                    .increment();
            log.error(
                    "알림 워크플로가 ShedLock 최대 실행시간을 초과했습니다. "
                            + "trigger={}, durationMillis={}, lockAtMostFor={}",
                    trigger,
                    duration.toMillis(),
                    LOCK_AT_MOST_FOR
            );
        }
    }

    public void recordFinalRecoveryFailure(
            LocalDate targetDate,
            RuntimeException exception
    ) {
        Counter.builder("planit.notification.workflow.recovery_failures")
                .description("11:00 알림 복구 워크플로의 최종 실패 횟수")
                .register(meterRegistry)
                .increment();
        log.error(
                "알림 복구 워크플로가 최종 실패했습니다. targetDate={}",
                targetDate,
                exception
        );
    }
}
