package com.planit.notification.precheck;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class TripRemindNotificationObservabilityTest {

    @DisplayName("워크플로 실행시간과 결과를 메트릭으로 기록한다")
    @Test
    void recordsExecutionMetric() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TripRemindNotificationObservability observability =
                new TripRemindNotificationObservability(registry);

        observability.recordExecution(
                "primary",
                "success",
                Duration.ofSeconds(2)
        );

        assertThat(registry.get("planit.notification.workflow.duration")
                .tags("trigger", "primary", "outcome", "success")
                .timer()
                .count()).isEqualTo(1L);
    }

    @DisplayName("ShedLock 최대 실행시간 이상이면 카운터와 오류 로그를 기록한다")
    @Test
    void recordsLockAtMostExceeded(CapturedOutput output) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TripRemindNotificationObservability observability =
                new TripRemindNotificationObservability(registry);

        observability.recordExecution(
                "recovery",
                "success",
                Duration.ofMinutes(5)
        );

        assertThat(registry.get(
                        "planit.notification.workflow.lock_at_most_exceeded"
                )
                .tag("trigger", "recovery")
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(output).contains("ShedLock 최대 실행시간을 초과했습니다");
    }

    @DisplayName("10시 30분 복구 실패를 최종 장애로 기록한다")
    @Test
    void recordsFinalRecoveryFailure(CapturedOutput output) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TripRemindNotificationObservability observability =
                new TripRemindNotificationObservability(registry);

        observability.recordFinalRecoveryFailure(
                LocalDate.of(2026, 10, 18),
                new IllegalStateException("AI 장애")
        );

        assertThat(registry.get(
                        "planit.notification.workflow.recovery_failures"
                )
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(output).contains(
                "알림 복구 워크플로가 최종 실패했습니다. targetDate=2026-10-18"
        );
    }
}
