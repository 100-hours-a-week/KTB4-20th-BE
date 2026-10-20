package com.planit.notification.repository;

import com.planit.domain.User;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationType;
import com.planit.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationStoreConcurrencyTest {

    @Autowired
    private NotificationStore notificationStore;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DisplayName("같은 중복 방지 키를 동시에 저장해도 알림은 하나만 생성된다")
    @Test
    void concurrentlySavesOneNotificationPerDedupeKey() throws Exception {
        User recipient = userRepository.save(new User(
                null,
                UUID.randomUUID(),
                "알림동시성테스트"
        ));
        Notification first = notification(recipient, "첫 번째 알림");
        Notification second = notification(recipient, "두 번째 알림");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Long>> results = List.of(
                    executor.submit(() -> saveAfterStart(first, ready, start)),
                    executor.submit(() -> saveAfterStart(second, ready, start))
            );
            ready.await();
            start.countDown();

            Long firstId = results.get(0).get();
            Long secondId = results.get(1).get();
            Integer savedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE dedupe_key = ?",
                    Integer.class,
                    first.getDedupeKey()
            );

            assertThat(secondId).isEqualTo(firstId);
            assertThat(savedCount).isEqualTo(1);
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM notifications WHERE dedupe_key = ?",
                    first.getDedupeKey()
            );
            userRepository.deleteById(recipient.getId());
        }
    }

    @DisplayName("같은 알림을 동시에 수신 확인해도 최초 전달 시각을 함께 반환한다")
    @Test
    void concurrentlyAcknowledgesDeliveryOnce() throws Exception {
        User recipient = userRepository.save(new User(
                null,
                UUID.randomUUID(),
                "알림ACK동시성테스트"
        ));
        Notification notification = notification(recipient, "수신 확인 알림");
        Long notificationId = notificationStore.saveIdempotently(notification);
        LocalDateTime firstAttempt = LocalDateTime.of(
                2026,
                10,
                17,
                11,
                0
        );
        LocalDateTime secondAttempt = firstAttempt.plusSeconds(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Optional<LocalDateTime>>> results = List.of(
                    executor.submit(() -> acknowledgeAfterStart(
                            notificationId,
                            recipient.getId(),
                            firstAttempt,
                            ready,
                            start
                    )),
                    executor.submit(() -> acknowledgeAfterStart(
                            notificationId,
                            recipient.getId(),
                            secondAttempt,
                            ready,
                            start
                    ))
            );
            ready.await();
            start.countDown();

            LocalDateTime firstResult = results.get(0).get().orElseThrow();
            LocalDateTime secondResult = results.get(1).get().orElseThrow();
            LocalDateTime stored = jdbcTemplate.queryForObject(
                    "SELECT delivered_at FROM notifications WHERE id = ?",
                    LocalDateTime.class,
                    notificationId
            );

            assertThat(firstResult).isEqualTo(secondResult);
            assertThat(stored).isEqualTo(firstResult);
            assertThat(stored).isIn(firstAttempt, secondAttempt);
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM notifications WHERE id = ?",
                    notificationId
            );
            userRepository.deleteById(recipient.getId());
        }
    }

    @DisplayName("먼저 다른 조회를 한 트랜잭션에서 동시에 수신 확인해도 늦은 요청이 최초 전달 시각을 받는다")
    @Test
    void concurrentlyAcknowledgesDeliveryAfterSnapshotRead() throws Exception {
        User recipient = userRepository.save(new User(
                null,
                UUID.randomUUID(),
                "알림ACK스냅샷테스트"
        ));
        Notification notification = notification(recipient, "스냅샷 수신 확인 알림");
        Long notificationId = notificationStore.saveIdempotently(notification);
        LocalDateTime firstAttempt = LocalDateTime.of(2026, 10, 17, 11, 0);
        CountDownLatch firstUpdated = new CountDownLatch(1);
        CountDownLatch secondReadSnapshot = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try (var executor = Executors.newFixedThreadPool(2)) {
            // 먼저 ACK한 요청: 갱신한 뒤 늦은 요청이 스냅샷을 만들 때까지 커밋을 미룬다.
            Future<Optional<LocalDateTime>> first = executor.submit(() ->
                    transaction.execute(status -> {
                        Optional<LocalDateTime> result = notificationStore.acknowledgeDelivery(
                                notificationId,
                                recipient.getId(),
                                firstAttempt
                        );
                        firstUpdated.countDown();
                        await(secondReadSnapshot);
                        return result;
                    })
            );
            // 늦은 요청: 서비스처럼 사용자를 먼저 조회해 스냅샷을 만든 뒤 ACK한다.
            Future<Optional<LocalDateTime>> second = executor.submit(() ->
                    transaction.execute(status -> {
                        await(firstUpdated);
                        jdbcTemplate.queryForObject(
                                "SELECT id FROM users WHERE id = ?",
                                Long.class,
                                recipient.getId()
                        );
                        secondReadSnapshot.countDown();
                        return notificationStore.acknowledgeDelivery(
                                notificationId,
                                recipient.getId(),
                                firstAttempt.plusSeconds(1)
                        );
                    })
            );

            assertThat(first.get()).contains(firstAttempt);
            assertThat(second.get()).contains(firstAttempt);
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM notifications WHERE id = ?",
                    notificationId
            );
            userRepository.deleteById(recipient.getId());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private Long saveAfterStart(
            Notification notification,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return notificationStore.saveIdempotently(notification);
    }

    private Optional<LocalDateTime> acknowledgeAfterStart(
            Long notificationId,
            Long recipientId,
            LocalDateTime acknowledgedAt,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return notificationStore.acknowledgeDelivery(
                notificationId,
                recipientId,
                acknowledgedAt
        );
    }

    private Notification notification(User recipient, String title) {
        return Notification.createTripNotification(
                recipient,
                1001L,
                LocalDate.of(2026, 10, 15),
                NotificationType.TRIP_D1,
                title,
                "여행 일정과 날씨를 확인해 주세요.",
                LocalDateTime.of(2026, 10, 14, 10, 0)
        );
    }
}
