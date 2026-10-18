package com.planit.notification.service;

import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.notification.repository.NotificationStore;
import com.planit.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeliveryServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-17T02:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @DisplayName("현재 사용자의 알림 수신 시각을 멱등 저장하고 반환한다")
    @Test
    void acknowledgesOwnedNotification() {
        UserRepository userRepository = mock(UserRepository.class);
        NotificationStore notificationStore = mock(NotificationStore.class);
        NotificationDeliveryService service = new NotificationDeliveryService(
                userRepository,
                notificationStore,
                CLOCK
        );
        UUID recipientPublicId = UUID.randomUUID();
        User recipient = mock(User.class);
        LocalDateTime deliveredAt = LocalDateTime.of(
                2026,
                10,
                17,
                10,
                30
        );
        when(recipient.getId()).thenReturn(100L);
        when(userRepository.findByPublicIdAndDeletedAtIsNull(
                recipientPublicId
        )).thenReturn(Optional.of(recipient));
        when(notificationStore.acknowledgeDelivery(
                16001L,
                100L,
                LocalDateTime.of(2026, 10, 17, 11, 0)
        )).thenReturn(Optional.of(deliveredAt));

        service.acknowledge(
                recipientPublicId.toString(),
                16001L
        );

        verify(notificationStore).acknowledgeDelivery(
                16001L,
                100L,
                LocalDateTime.of(2026, 10, 17, 11, 0)
        );
    }

    @DisplayName("소유하지 않았거나 삭제된 알림은 찾을 수 없음으로 처리한다")
    @Test
    void rejectsUnavailableNotification() {
        UserRepository userRepository = mock(UserRepository.class);
        NotificationStore notificationStore = mock(NotificationStore.class);
        NotificationDeliveryService service = new NotificationDeliveryService(
                userRepository,
                notificationStore,
                CLOCK
        );
        UUID recipientPublicId = UUID.randomUUID();
        User recipient = mock(User.class);
        when(recipient.getId()).thenReturn(100L);
        when(userRepository.findByPublicIdAndDeletedAtIsNull(
                recipientPublicId
        )).thenReturn(Optional.of(recipient));
        when(notificationStore.acknowledgeDelivery(
                16001L,
                100L,
                LocalDateTime.of(2026, 10, 17, 11, 0)
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acknowledge(
                recipientPublicId.toString(),
                16001L
        )).isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }
}
