package com.planit.notification.precheck;

import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TripRemindNotificationSchedulerTest {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private TripRemindNotificationWorkflow workflow;
    private TripRemindNotificationObservability observability;
    private TripRemindNotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        workflow = mock(TripRemindNotificationWorkflow.class);
        observability = mock(TripRemindNotificationObservability.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-14T16:30:00Z"),
                SEOUL_ZONE
        );
        scheduler = new TripRemindNotificationScheduler(
                workflow,
                observability,
                clock
        );
        LockAssert.TestHelper.makeAllAssertsPass(true);
    }

    @AfterEach
    void tearDown() {
        LockAssert.TestHelper.makeAllAssertsPass(false);
    }

    @DisplayName("첫 실행은 서울 기준 다음 날을 대상으로 워크플로를 실행한다")
    @Test
    void runsPrimaryForNextSeoulDate() {
        scheduler.runPrimary();

        verify(workflow).execute(LocalDate.of(2026, 10, 16));
    }

    @DisplayName("복구 실행도 동일한 대상일과 워크플로를 사용한다")
    @Test
    void runsRecoveryForSameNextSeoulDate() {
        scheduler.runPrimary();
        scheduler.runRecovery();

        verify(workflow, times(2)).execute(LocalDate.of(2026, 10, 16));
    }

    @DisplayName("첫 실행과 복구 실행은 정해진 서울 시각에 예약된다")
    @Test
    void schedulesPrimaryAndRecoveryInSeoul() throws Exception {
        Scheduled primary = method("runPrimary")
                .getAnnotation(Scheduled.class);
        Scheduled recovery = method("runRecovery")
                .getAnnotation(Scheduled.class);

        assertThat(primary.cron()).isEqualTo("0 45 14 * * *");
        assertThat(primary.zone()).isEqualTo("Asia/Seoul");
        assertThat(recovery.cron()).isEqualTo("0 55 14 * * *");
        assertThat(recovery.zone()).isEqualTo("Asia/Seoul");
    }

    @DisplayName("첫 실행과 복구 실행은 동일한 ShedLock 설정을 사용한다")
    @Test
    void usesSameShedLockForBothSchedules() throws Exception {
        SchedulerLock primary = method("runPrimary")
                .getAnnotation(SchedulerLock.class);
        SchedulerLock recovery = method("runRecovery")
                .getAnnotation(SchedulerLock.class);

        assertThat(primary.name()).isEqualTo(
                "tripRemindNotificationWorkflow"
        );
        assertThat(recovery.name()).isEqualTo(primary.name());
        assertThat(primary.lockAtMostFor()).isEqualTo("PT5M");
        assertThat(primary.lockAtLeastFor()).isEqualTo("PT1M");
        assertThat(recovery.lockAtMostFor()).isEqualTo("PT5M");
        assertThat(recovery.lockAtLeastFor()).isEqualTo("PT1M");
    }

    @DisplayName("복구 실행이 실패하면 최종 실패와 실행 결과를 기록한다")
    @Test
    void recordsFinalRecoveryFailure() {
        IllegalStateException failure = new IllegalStateException(
                "복구 실패"
        );
        doThrow(failure).when(workflow)
                .execute(LocalDate.of(2026, 10, 16));

        assertThatThrownBy(scheduler::runRecovery)
                .isSameAs(failure);

        verify(observability).recordFinalRecoveryFailure(
                LocalDate.of(2026, 10, 16),
                failure
        );
        verify(observability).recordExecution(
                eq("recovery"),
                eq("failure"),
                any(java.time.Duration.class)
        );
    }

    private static Method method(String name) throws NoSuchMethodException {
        return TripRemindNotificationScheduler.class.getMethod(name);
    }
}
