package com.planit.notification.precheck;

import com.planit.domain.Trip;
import com.planit.domain.User;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.repository.NotificationStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripRemindNotificationGenerationServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-15T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @DisplayName("산출한 대상별 알림을 멱등 저장한다")
    @Test
    void idempotentlySavesGeneratedNotifications() {
        LocalDate targetDate = LocalDate.of(2026, 10, 16);
        TripRemindNotificationTargetService targetService =
                mock(TripRemindNotificationTargetService.class);
        NotificationStore notificationStore = mock(NotificationStore.class);
        TripRemindNotificationGenerationService service =
                new TripRemindNotificationGenerationService(
                        targetService,
                        notificationStore,
                        CLOCK
                );
        Trip normalTrip = trip(1L, targetDate);
        Trip weatherTrip = trip(2L, targetDate);
        User normalRecipient = user("0199f4de-5010-7b4a-91d8-36ca51a76b21");
        User weatherRecipient = user("0199f4de-5010-7b4a-91d8-36ca51a76b22");
        when(targetService.findTargets(targetDate)).thenReturn(List.of(
                new TripRemindNotificationTarget(
                        normalTrip,
                        normalRecipient,
                        NotificationType.TRIP_D1
                ),
                new TripRemindNotificationTarget(
                        weatherTrip,
                        weatherRecipient,
                        NotificationType.TRIP_WEATHER_ALERT
                )
        ));
        service.generate(targetDate);

        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);
        verify(notificationStore, org.mockito.Mockito.times(2))
                .saveIdempotently(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Notification::getType)
                .containsExactly(
                        NotificationType.TRIP_D1,
                        NotificationType.TRIP_WEATHER_ALERT
                );
        assertThat(captor.getAllValues())
                .extracting(Notification::getTitle)
                .containsExactly(
                        "내일 여행이 시작돼요",
                        "여행지에 특이 기상이 예상돼요"
                );
        assertThat(captor.getAllValues())
                .extracting(Notification::getCreatedAt)
                .containsOnly(LocalDateTime.of(2026, 10, 15, 10, 0));
    }

    private Trip trip(Long id, LocalDate startDate) {
        Trip trip = mock(Trip.class);
        when(trip.getId()).thenReturn(id);
        when(trip.getStartDate()).thenReturn(startDate);
        return trip;
    }

    private User user(String publicId) {
        User user = mock(User.class);
        when(user.getPublicId()).thenReturn(UUID.fromString(publicId));
        return user;
    }
}
