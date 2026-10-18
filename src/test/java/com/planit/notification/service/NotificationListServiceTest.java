package com.planit.notification.service;

import com.planit.domain.Trip;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationTargetStatus;
import com.planit.notification.domain.NotificationTargetType;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.pagination.NotificationListCursorCodec;
import com.planit.notification.repository.NotificationRepository;
import com.planit.repository.TripRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationListServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-17T02:00:00Z"),
            java.time.ZoneId.of("Asia/Seoul")
    );

    @DisplayName("현재 사용자의 미읽음 알림 수와 존재 여부를 반환한다")
    @Test
    void returnsUnreadCount() {
        NotificationRepository notificationRepository =
                mock(NotificationRepository.class);
        NotificationListService service = new NotificationListService(
                notificationRepository,
                mock(TripRepository.class),
                new NotificationListCursorCodec(),
                CLOCK
        );
        UUID recipientPublicId = UUID.randomUUID();
        when(notificationRepository
                .countByRecipientPublicIdAndReadAtIsNullAndDeletedAtIsNull(
                        recipientPublicId
                )).thenReturn(3L);

        var response = service.getUnreadCount(recipientPublicId.toString());

        assertThat(response.unreadCount()).isEqualTo(3L);
        assertThat(response.hasUnread()).isTrue();
    }

    @DisplayName("미읽음 알림이 없으면 hasUnread를 false로 반환한다")
    @Test
    void returnsNoUnreadNotifications() {
        NotificationRepository notificationRepository =
                mock(NotificationRepository.class);
        NotificationListService service = new NotificationListService(
                notificationRepository,
                mock(TripRepository.class),
                new NotificationListCursorCodec(),
                CLOCK
        );
        UUID recipientPublicId = UUID.randomUUID();

        var response = service.getUnreadCount(recipientPublicId.toString());

        assertThat(response.unreadCount()).isZero();
        assertThat(response.hasUnread()).isFalse();
    }

    @DisplayName("첫 페이지 20개와 다음 커서를 생성하고 삭제된 여행 대상을 만료 처리한다")
    @Test
    void returnsFirstPageWithCurrentTargetStatus() {
        NotificationRepository notificationRepository =
                mock(NotificationRepository.class);
        TripRepository tripRepository = mock(TripRepository.class);
        NotificationListCursorCodec cursorCodec =
                new NotificationListCursorCodec();
        NotificationListService service = new NotificationListService(
                notificationRepository,
                tripRepository,
                cursorCodec,
                CLOCK
        );
        UUID recipientPublicId = UUID.randomUUID();
        List<Notification> notifications = new ArrayList<>();
        for (long id = 21; id >= 1; id--) {
            notifications.add(notification(
                    id,
                    id == 21 ? 1001L : 1002L,
                    LocalDateTime.of(2026, 10, 16, 10, 0)
                            .minusMinutes(21 - id)
            ));
        }
        when(notificationRepository.findPageForRecipient(
                recipientPublicId,
                null,
                null,
                PageRequest.of(0, 21)
        )).thenReturn(notifications);
        Trip activeTrip = mock(Trip.class);
        when(activeTrip.getId()).thenReturn(1001L);
        when(activeTrip.getDeletedAt()).thenReturn(null);
        when(tripRepository.findAllById(List.of(1001L, 1002L)))
                .thenReturn(List.of(activeTrip));

        var response = service.getNotifications(
                recipientPublicId.toString(),
                null
        );

        assertThat(response.items()).hasSize(20);
        assertThat(response.items().getFirst().target().status())
                .isEqualTo(NotificationTargetStatus.ACTIVE);
        assertThat(response.items().get(1).target().status())
                .isEqualTo(NotificationTargetStatus.EXPIRED);
        assertThat(response.items().getFirst().createdAt())
                .isEqualTo(OffsetDateTime.parse(
                        "2026-10-16T10:00:00+09:00"
                ));
        assertThat(response.page().hasNext()).isTrue();
        assertThat(response.page().nextCursor()).isNotBlank();
        verify(notificationRepository).findPageForRecipient(
                recipientPublicId,
                null,
                null,
                PageRequest.of(0, 21)
        );
    }

    @DisplayName("커서 이후 페이지는 10개를 조회하고 빈 페이지 규약을 지킨다")
    @Test
    void returnsEmptyNextPage() {
        NotificationRepository notificationRepository =
                mock(NotificationRepository.class);
        TripRepository tripRepository = mock(TripRepository.class);
        NotificationListCursorCodec cursorCodec =
                new NotificationListCursorCodec();
        NotificationListService service = new NotificationListService(
                notificationRepository,
                tripRepository,
                cursorCodec,
                CLOCK
        );
        UUID recipientPublicId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 16, 10, 0);
        String cursor = cursorCodec.encode(
                new NotificationListCursorCodec.Cursor(
                        recipientPublicId,
                        createdAt,
                        16001L
                )
        );
        when(notificationRepository.findPageForRecipient(
                recipientPublicId,
                createdAt,
                16001L,
                PageRequest.of(0, 11)
        )).thenReturn(List.of());

        var response = service.getNotifications(
                recipientPublicId.toString(),
                cursor
        );

        assertThat(response.items()).isEmpty();
        assertThat(response.page().nextCursor()).isNull();
        assertThat(response.page().hasNext()).isFalse();
    }

    @DisplayName("서울 기준 당일의 현재 사용자 미전달 알림을 반환한다")
    @Test
    void returnsPendingDeliveriesForToday() {
        NotificationRepository notificationRepository =
                mock(NotificationRepository.class);
        NotificationListService service = new NotificationListService(
                notificationRepository,
                mock(TripRepository.class),
                new NotificationListCursorCodec(),
                CLOCK
        );
        UUID recipientPublicId = UUID.randomUUID();
        Notification notification = notification(
                16001L,
                1001L,
                LocalDateTime.of(2026, 10, 17, 10, 0)
        );
        when(notificationRepository.findPendingDeliveriesForRecipient(
                recipientPublicId,
                LocalDateTime.of(2026, 10, 17, 0, 0),
                LocalDateTime.of(2026, 10, 18, 0, 0),
                List.of(
                        NotificationType.TRIP_D1,
                        NotificationType.TRIP_WEATHER_ALERT
                )
        )).thenReturn(List.of(notification));

        var response = service.getPendingDeliveries(
                recipientPublicId.toString()
        );

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().notificationId())
                .isEqualTo("16001");
        assertThat(response.items().getFirst().createdAt())
                .isEqualTo(OffsetDateTime.parse(
                        "2026-10-17T10:00:00+09:00"
                ));
    }

    private Notification notification(
            Long id,
            Long targetId,
            LocalDateTime createdAt
    ) {
        Notification notification = mock(Notification.class);
        when(notification.getId()).thenReturn(id);
        when(notification.getType()).thenReturn(NotificationType.TRIP_D1);
        when(notification.getTitle()).thenReturn("알림 제목");
        when(notification.getBody()).thenReturn("알림 본문");
        when(notification.getTargetType())
                .thenReturn(NotificationTargetType.TRIP);
        when(notification.getTargetId()).thenReturn(targetId);
        when(notification.getTargetStatus())
                .thenReturn(NotificationTargetStatus.ACTIVE);
        when(notification.getCreatedAt()).thenReturn(createdAt);
        return notification;
    }
}
