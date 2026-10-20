package com.planit.notification.precheck;

import com.planit.notification.delivery.NotificationEventPublisher;
import com.planit.notification.delivery.NotificationPublishTargetService;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
class TripRemindNotificationRecoveryIntegrationTest {

    private static final LocalDate PRECHECKED_DATE =
            LocalDate.of(2099, 1, 11);
    private static final LocalDate GENERATED_DATE =
            LocalDate.of(2099, 1, 12);
    private static final LocalDate DONE_DATE =
            LocalDate.of(2099, 1, 13);

    @Autowired
    private TripRemindPrecheckStore precheckStore;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private LockProvider lockProvider;

    @AfterEach
    void cleanUp() {
        redisTemplate.delete(List.of(
                key(PRECHECKED_DATE),
                key(GENERATED_DATE),
                key(DONE_DATE)
        ));
    }

    @DisplayName("두 실행기가 같은 Redis 락에 접근하면 하나만 작업을 수행한다")
    @Test
    void allowsOnlyOneInstanceToExecute() throws Exception {
        String lockName = "trip-remind-integration-" + UUID.randomUUID();
        DefaultLockingTaskExecutor firstExecutor =
                new DefaultLockingTaskExecutor(lockProvider);
        DefaultLockingTaskExecutor secondExecutor =
                new DefaultLockingTaskExecutor(lockProvider);
        CountDownLatch firstAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger executionCount = new AtomicInteger();

        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> firstExecutor.executeWithLock(
                    (Runnable) () -> {
                        executionCount.incrementAndGet();
                        firstAcquired.countDown();
                        await(releaseFirst);
                    },
                    lockConfiguration(lockName)
            ));
            try {
                assertThat(firstAcquired.await(5, TimeUnit.SECONDS)).isTrue();
                secondExecutor.executeWithLock(
                        (Runnable) executionCount::incrementAndGet,
                        lockConfiguration(lockName)
                );
            } finally {
                releaseFirst.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
        }

        assertThat(executionCount).hasValue(1);
    }

    @DisplayName("PRECHECKED 상태는 생성과 발행을 재개해 DONE이 된다")
    @Test
    void resumesFromPrechecked() {
        create(PRECHECKED_DATE);
        WorkflowFixture fixture = fixture(PRECHECKED_DATE);

        fixture.workflow().execute(PRECHECKED_DATE);

        verify(fixture.generationService()).generate(PRECHECKED_DATE);
        assertStatus(PRECHECKED_DATE, TripRemindNotificationWorkflowStatus.DONE);
    }

    @DisplayName("NOTI_GENERATED 상태는 발행만 재개해 DONE이 된다")
    @Test
    void resumesFromNotificationGenerated() {
        create(GENERATED_DATE);
        transition(
                GENERATED_DATE,
                TripRemindNotificationWorkflowStatus.PRECHECKED,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED
        );
        WorkflowFixture fixture = fixture(GENERATED_DATE);

        fixture.workflow().execute(GENERATED_DATE);

        verify(fixture.generationService(), never()).generate(GENERATED_DATE);
        verify(fixture.publishTargetService()).findTodayUndelivered();
        assertStatus(GENERATED_DATE, TripRemindNotificationWorkflowStatus.DONE);
    }

    @DisplayName("DONE 상태는 생성과 발행을 다시 수행하지 않는다")
    @Test
    void stopsAtDone() {
        create(DONE_DATE);
        transition(
                DONE_DATE,
                TripRemindNotificationWorkflowStatus.PRECHECKED,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED
        );
        transition(
                DONE_DATE,
                TripRemindNotificationWorkflowStatus.NOTI_GENERATED,
                TripRemindNotificationWorkflowStatus.DONE
        );
        WorkflowFixture fixture = fixture(DONE_DATE);

        fixture.workflow().execute(DONE_DATE);

        verifyNoInteractions(
                fixture.generationService(),
                fixture.publishTargetService(),
                fixture.eventPublisher()
        );
        assertStatus(DONE_DATE, TripRemindNotificationWorkflowStatus.DONE);
    }

    private WorkflowFixture fixture(LocalDate targetDate) {
        TripRemindPrecheckService precheckService =
                mock(TripRemindPrecheckService.class);
        TripRemindNotificationGenerationService generationService =
                mock(TripRemindNotificationGenerationService.class);
        NotificationPublishTargetService publishTargetService =
                mock(NotificationPublishTargetService.class);
        NotificationEventPublisher eventPublisher =
                mock(NotificationEventPublisher.class);
        when(precheckService.createIfAbsent(targetDate))
                .thenReturn(TripRemindPrecheckCreateResult.ALREADY_EXISTS);
        when(publishTargetService.findTodayUndelivered())
                .thenReturn(List.of());

        return new WorkflowFixture(
                new TripRemindNotificationWorkflow(
                        precheckService,
                        precheckStore,
                        generationService,
                        publishTargetService,
                        eventPublisher
                ),
                generationService,
                publishTargetService,
                eventPublisher
        );
    }

    private void create(LocalDate targetDate) {
        assertThat(precheckStore.create(
                targetDate,
                new TripRemindPrecheckCache(
                        Instant.parse("2099-01-10T01:00:00Z"),
                        TripRemindNotificationWorkflowStatus.PRECHECKED,
                        List.of()
                )
        )).isEqualTo(TripRemindPrecheckCreateResult.CREATED);
    }

    private void transition(
            LocalDate targetDate,
            TripRemindNotificationWorkflowStatus expected,
            TripRemindNotificationWorkflowStatus next
    ) {
        assertThat(precheckStore.transition(targetDate, expected, next))
                .isEqualTo(TripRemindPrecheckTransitionResult.UPDATED);
    }

    private void assertStatus(
            LocalDate targetDate,
            TripRemindNotificationWorkflowStatus expected
    ) {
        assertThat(precheckStore.find(targetDate).orElseThrow()
                .cache().status()).isEqualTo(expected);
    }

    private LockConfiguration lockConfiguration(String name) {
        return new LockConfiguration(
                Instant.now(),
                name,
                Duration.ofSeconds(10),
                Duration.ZERO
        );
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private String key(LocalDate targetDate) {
        return TripRemindPrecheckStore.KEY_PREFIX + targetDate;
    }

    private record WorkflowFixture(
            TripRemindNotificationWorkflow workflow,
            TripRemindNotificationGenerationService generationService,
            NotificationPublishTargetService publishTargetService,
            NotificationEventPublisher eventPublisher
    ) {
    }
}
