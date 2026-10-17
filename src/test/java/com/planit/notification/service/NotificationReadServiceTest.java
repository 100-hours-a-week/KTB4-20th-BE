package com.planit.notification.service;

import com.planit.domain.Trip;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationTargetStatus;
import com.planit.notification.repository.NotificationRepository;
import com.planit.repository.TripRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationReadServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-17T02:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @DisplayName("현재 사용자의 모든 미읽음 알림을 같은 시각으로 처리한다")
    @Test
    void marksAllNotificationsAsRead() {
        NotificationRepository notificationRepository =
                mock(NotificationRepository.class);
        NotificationReadService service = new NotificationReadService(
                notificationRepository,
                mock(TripRepository.class),
                CLOCK
        );
        UUID recipientPublicId = UUID.randomUUID();
        LocalDateTime readAt = LocalDateTime.of(2026, 10, 17, 11, 0);
        when(notificationRepository.markAllAsRead(
                recipientPublicId,
                readAt
        )).thenReturn(3);

        var response = service.markAllAsRead(recipientPublicId.toString());

        assertThat(response.updatedCount()).isEqualTo(3);
        assertThat(response.readAt().toString())
                .isEqualTo("2026-10-17T11:00+09:00");
        assertThat(response.unreadCount()).isZero();
        verify(notificationRepository).markAllAsRead(
                recipientPublicId,
                readAt
        );
        verify(notificationRepository)
                .countByRecipientPublicIdAndReadAtIsNullAndDeletedAtIsNull(
                        recipientPublicId
                );
    }

    @DisplayName("이미 모두 읽었으면 갱신 건수가 0이다")
    @Test
    void returnsZeroWhenAllNotificationsAreRead() {
        NotificationRepository notificationRepository =
                mock(NotificationRepository.class);
        NotificationReadService service = new NotificationReadService(
                notificationRepository,
                mock(TripRepository.class),
                CLOCK
        );
        UUID recipientPublicId = UUID.randomUUID();

        var response = service.markAllAsRead(recipientPublicId.toString());

        assertThat(response.updatedCount()).isZero();
        assertThat(response.unreadCount()).isZero();
    }

    @DisplayName("알림을 최초 시각으로 읽음 처리하고 활성 대상을 반환한다")
    @Test
    void marksNotificationAsRead() {
        NotificationRepository notificationRepository =
                mock(NotificationRepository.class);
        TripRepository tripRepository = mock(TripRepository.class);
        NotificationReadService service = new NotificationReadService(
                notificationRepository,
                tripRepository,
                CLOCK
        );
        UUID recipientPublicId = UUID.randomUUID();
        Notification notification = mock(Notification.class);
        Trip trip = mock(Trip.class);
        when(notificationRepository.findOwnedActiveById(
                16001L,
                recipientPublicId
        )).thenReturn(Optional.of(notification));
        when(notification.getId()).thenReturn(16001L);
        when(notification.getTargetId()).thenReturn(1001L);
        when(notification.getTargetStatus())
                .thenReturn(NotificationTargetStatus.ACTIVE);
        when(notification.getReadAt()).thenReturn(
                LocalDateTime.of(2026, 10, 17, 11, 0)
        );
        when(tripRepository.findById(1001L)).thenReturn(Optional.of(trip));
        when(trip.getDeletedAt()).thenReturn(null);

        var response = service.markAsRead(
                recipientPublicId.toString(),
                16001L
        );

        verify(notification).markAsRead(
                LocalDateTime.of(2026, 10, 17, 11, 0)
        );
        assertThat(response.read()).isTrue();
        assertThat(response.targetStatus())
                .isEqualTo(NotificationTargetStatus.ACTIVE);
        assertThat(response.navigable()).isTrue();
        assertThat(response.readAt().toString())
                .isEqualTo("2026-10-17T11:00+09:00");
    }

    @DisplayName("삭제된 여행 대상은 만료되어 이동할 수 없다고 반환한다")
    @Test
    void returnsExpiredTarget() {
        NotificationRepository notificationRepository =
                mock(NotificationRepository.class);
        TripRepository tripRepository = mock(TripRepository.class);
        NotificationReadService service = new NotificationReadService(
                notificationRepository,
                tripRepository,
                CLOCK
        );
        UUID recipientPublicId = UUID.randomUUID();
        Notification notification = mock(Notification.class);
        when(notificationRepository.findOwnedActiveById(
                16001L,
                recipientPublicId
        )).thenReturn(Optional.of(notification));
        when(notification.getId()).thenReturn(16001L);
        when(notification.getTargetId()).thenReturn(1001L);
        when(notification.getTargetStatus())
                .thenReturn(NotificationTargetStatus.ACTIVE);
        when(notification.getReadAt()).thenReturn(
                LocalDateTime.of(2026, 10, 17, 11, 0)
        );
        when(tripRepository.findById(1001L)).thenReturn(Optional.empty());

        var response = service.markAsRead(
                recipientPublicId.toString(),
                16001L
        );

        assertThat(response.targetStatus())
                .isEqualTo(NotificationTargetStatus.EXPIRED);
        assertThat(response.navigable()).isFalse();
    }

    @DisplayName("소유하지 않았거나 삭제된 알림은 찾을 수 없음으로 처리한다")
    @Test
    void rejectsUnavailableNotification() {
        NotificationRepository notificationRepository =
                mock(NotificationRepository.class);
        NotificationReadService service = new NotificationReadService(
                notificationRepository,
                mock(TripRepository.class),
                CLOCK
        );
        UUID recipientPublicId = UUID.randomUUID();
        when(notificationRepository.findOwnedActiveById(
                16001L,
                recipientPublicId
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(
                recipientPublicId.toString(),
                16001L
        )).isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }
}
