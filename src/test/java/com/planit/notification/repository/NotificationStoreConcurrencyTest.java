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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    private Long saveAfterStart(
            Notification notification,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return notificationStore.saveIdempotently(notification);
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
