package com.planit.notification.domain;

import com.planit.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {

    @DisplayName("순차적인 반복 읽음 처리는 기존 시각을 유지한다")
    @Test
    void preservesFirstReadAt() {
        Notification notification = Notification.createTripNotification(
                new User(null, UUID.randomUUID(), "읽음테스트"),
                1001L,
                LocalDate.of(2026, 10, 18),
                NotificationType.TRIP_D1,
                "알림 제목",
                "알림 본문",
                LocalDateTime.of(2026, 10, 17, 10, 0)
        );
        LocalDateTime firstReadAt = LocalDateTime.of(2026, 10, 17, 11, 0);

        notification.markAsRead(firstReadAt);
        notification.markAsRead(firstReadAt.plusMinutes(1));

        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
    }

    @DisplayName("여행 알림은 승인된 순서의 평문 중복 방지 키로 생성한다")
    @Test
    void createsTripNotificationWithPlaintextDedupeKey() {
        UUID recipientPublicId = UUID.fromString(
                "0199f4de-5010-7b4a-91d8-36ca51a76b21"
        );
        User recipient = new User(null, recipientPublicId, "알림테스트");
        LocalDateTime createdAt =
                LocalDateTime.of(2026, 10, 14, 10, 0);

        Notification notification = Notification.createTripNotification(
                recipient,
                1001L,
                LocalDate.of(2026, 10, 15),
                NotificationType.TRIP_D1,
                "내일 여행이 시작돼요",
                "여행 일정과 날씨를 확인해 주세요.",
                createdAt
        );

        assertThat(notification.getRecipient()).isSameAs(recipient);
        assertThat(notification.getType()).isEqualTo(NotificationType.TRIP_D1);
        assertThat(notification.getTargetType())
                .isEqualTo(NotificationTargetType.TRIP);
        assertThat(notification.getTargetId()).isEqualTo(1001L);
        assertThat(notification.getTargetStatus())
                .isEqualTo(NotificationTargetStatus.ACTIVE);
        assertThat(notification.getDedupeKey()).isEqualTo(
                "TRIP_D1:1001:0199f4de-5010-7b4a-91d8-36ca51a76b21:2026-10-15"
        );
        assertThat(notification.getCreatedAt()).isEqualTo(createdAt);
        assertThat(notification.getReadAt()).isNull();
        assertThat(notification.getDeliveredAt()).isNull();
        assertThat(notification.getDeletedAt()).isNull();
    }

    @DisplayName("수신자의 공개 ID가 없으면 여행 알림을 생성할 수 없다")
    @Test
    void requiresRecipientPublicId() {
        User recipient = new User(null, null, "알림테스트");

        assertThatThrownBy(() -> Notification.createTripNotification(
                recipient,
                1001L,
                LocalDate.of(2026, 10, 15),
                NotificationType.TRIP_D1,
                "내일 여행이 시작돼요",
                "여행 일정과 날씨를 확인해 주세요.",
                LocalDateTime.of(2026, 10, 14, 10, 0)
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("알림 수신자 공개 ID는 필수입니다");
    }
}
