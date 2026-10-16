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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationStore notificationStore;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DisplayName("같은 중복 방지 키의 알림은 최초 저장 내용을 유지한다")
    @Test
    void idempotentlySavesNotification() {
        UUID recipientPublicId = UUID.randomUUID();
        User recipient = userRepository.save(new User(
                null,
                recipientPublicId,
                "알림저장테스트"
        ));
        LocalDate tripStartDate = LocalDate.of(2026, 10, 15);
        LocalDateTime createdAt =
                LocalDateTime.of(2026, 10, 14, 10, 0);

        Long firstId = notificationStore.saveIdempotently(notification(
                recipient,
                tripStartDate,
                createdAt,
                "내일 여행이 시작돼요"
        ));
        LocalDateTime firstStoredCreatedAt = findCreatedAt(firstId);
        Long duplicateId = notificationStore.saveIdempotently(notification(
                recipient,
                tripStartDate,
                createdAt.plusMinutes(30),
                "재실행으로 변경된 제목"
        ));
        Notification saved = notificationRepository.findById(firstId)
                .orElseThrow();

        assertThat(duplicateId).isEqualTo(firstId);
        assertThat(countByDedupeKey(saved.getDedupeKey())).isEqualTo(1);
        assertThat(saved.getDedupeKey()).isEqualTo(
                "TRIP_D1:1001:" + recipientPublicId + ":2026-10-15"
        );
        assertThat(saved.getTitle()).isEqualTo("내일 여행이 시작돼요");
        assertThat(findCreatedAt(firstId)).isEqualTo(firstStoredCreatedAt);
    }

    @DisplayName("알림 수신 ACK는 최초 시각을 유지하고 소유자가 아니면 처리하지 않는다")
    @Test
    void idempotentlyAcknowledgesOwnedNotification() {
        User recipient = userRepository.save(new User(
                null,
                UUID.randomUUID(),
                "알림수신자"
        ));
        User otherUser = userRepository.save(new User(
                null,
                UUID.randomUUID(),
                "다른사용자"
        ));
        Long notificationId = notificationStore.saveIdempotently(notification(
                recipient,
                LocalDate.of(2026, 10, 15),
                LocalDateTime.of(2026, 10, 14, 10, 0),
                "내일 여행이 시작돼요"
        ));
        LocalDateTime firstAck =
                LocalDateTime.of(2026, 10, 14, 10, 1);

        assertThat(notificationStore.acknowledgeDelivery(
                notificationId,
                recipient.getId(),
                firstAck
        )).contains(firstAck);
        assertThat(notificationStore.acknowledgeDelivery(
                notificationId,
                recipient.getId(),
                firstAck.plusMinutes(1)
        )).contains(firstAck);
        assertThat(notificationStore.acknowledgeDelivery(
                notificationId,
                otherUser.getId(),
                firstAck.plusMinutes(2)
        )).isEmpty();
    }

    @DisplayName("소프트 삭제된 알림은 수신 ACK를 처리하지 않는다")
    @Test
    void doesNotAcknowledgeDeletedNotification() {
        User recipient = userRepository.save(new User(
                null,
                UUID.randomUUID(),
                "삭제알림수신자"
        ));
        Long notificationId = notificationStore.saveIdempotently(notification(
                recipient,
                LocalDate.of(2026, 10, 15),
                LocalDateTime.of(2026, 10, 14, 10, 0),
                "내일 여행이 시작돼요"
        ));
        jdbcTemplate.update(
                "UPDATE notifications SET deleted_at = ? WHERE id = ?",
                LocalDateTime.of(2026, 10, 14, 10, 1),
                notificationId
        );

        assertThat(notificationStore.acknowledgeDelivery(
                notificationId,
                recipient.getId(),
                LocalDateTime.of(2026, 10, 14, 10, 2)
        )).isEmpty();
    }

    @DisplayName("당일 미전달·미삭제 여행 알림만 발행 대상으로 조회한다")
    @Test
    void findsTodayUndeliveredNotifications() {
        User recipient = userRepository.save(new User(
                null,
                UUID.randomUUID(),
                "발행대상수신자"
        ));
        LocalDate targetDate = LocalDate.of(2026, 10, 17);
        LocalDateTime dayStart = LocalDateTime.of(2026, 10, 16, 0, 0);
        Long eligibleId = notificationStore.saveIdempotently(notification(
                recipient,
                2001L,
                targetDate,
                NotificationType.TRIP_D1,
                dayStart.plusHours(10)
        ));
        Long deliveredId = notificationStore.saveIdempotently(notification(
                recipient,
                2002L,
                targetDate,
                NotificationType.TRIP_WEATHER_ALERT,
                dayStart.plusHours(10)
        ));
        Long deletedId = notificationStore.saveIdempotently(notification(
                recipient,
                2003L,
                targetDate,
                NotificationType.TRIP_D1,
                dayStart.plusHours(10)
        ));
        notificationStore.saveIdempotently(notification(
                recipient,
                2004L,
                targetDate,
                NotificationType.TRIP_D1,
                dayStart.minusSeconds(1)
        ));
        jdbcTemplate.update(
                "UPDATE notifications SET delivered_at = ? WHERE id = ?",
                dayStart.plusHours(10).plusMinutes(1),
                deliveredId
        );
        jdbcTemplate.update(
                "UPDATE notifications SET deleted_at = ? WHERE id = ?",
                dayStart.plusHours(10).plusMinutes(1),
                deletedId
        );

        List<Notification> notifications =
                notificationRepository.findUndeliveredCreatedBetween(
                        dayStart,
                        dayStart.plusDays(1),
                        List.of(
                                NotificationType.TRIP_D1,
                                NotificationType.TRIP_WEATHER_ALERT
                        )
                );

        assertThat(notifications)
                .extracting(Notification::getId)
                .containsExactly(eligibleId);
        assertThat(notifications.getFirst().getRecipient().getPublicId())
                .isEqualTo(recipient.getPublicId());
    }

    private Notification notification(
            User recipient,
            LocalDate tripStartDate,
            LocalDateTime createdAt,
            String title
    ) {
        return Notification.createTripNotification(
                recipient,
                1001L,
                tripStartDate,
                NotificationType.TRIP_D1,
                title,
                "여행 일정과 날씨를 확인해 주세요.",
                createdAt
        );
    }

    private Notification notification(
            User recipient,
            Long tripId,
            LocalDate tripStartDate,
            NotificationType type,
            LocalDateTime createdAt
    ) {
        return Notification.createTripNotification(
                recipient,
                tripId,
                tripStartDate,
                type,
                "알림 제목",
                "알림 본문",
                createdAt
        );
    }

    private LocalDateTime findCreatedAt(Long notificationId) {
        return jdbcTemplate.queryForObject(
                "SELECT created_at FROM notifications WHERE id = ?",
                LocalDateTime.class,
                notificationId
        );
    }

    private Integer countByDedupeKey(String dedupeKey) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE dedupe_key = ?",
                Integer.class,
                dedupeKey
        );
    }
}
