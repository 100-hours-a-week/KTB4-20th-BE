package com.planit.notification.delivery;

import com.planit.domain.User;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.domain.NotificationTargetType;
import com.planit.notification.domain.WeatherCondition;
import com.planit.notification.dto.NotificationTargetResponse;
import com.planit.notification.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationPublishTargetServiceTest {

    @DisplayName("서울 기준 당일 미전달 알림을 내부 발행 이벤트로 변환한다")
    @Test
    void findsTodayUndeliveredEvents() {
        NotificationRepository repository = mock(NotificationRepository.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-16T01:00:00Z"),
                ZoneId.of("Asia/Seoul")
        );
        NotificationPublishTargetService service =
                new NotificationPublishTargetService(repository, clock);
        UUID recipientPublicId = UUID.randomUUID();
        Notification notification = mock(Notification.class);
        User recipient = mock(User.class);
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 16, 10, 0);
        when(notification.getRecipient()).thenReturn(recipient);
        when(recipient.getPublicId()).thenReturn(recipientPublicId);
        when(notification.getId()).thenReturn(16001L);
        when(notification.getType())
                .thenReturn(NotificationType.TRIP_WEATHER_ALERT);
        when(notification.getTitle()).thenReturn("여행지에 특이 기상이 예상돼요");
        when(notification.getBody()).thenReturn("여행 기간의 날씨를 확인해 주세요.");
        when(notification.getCreatedAt()).thenReturn(createdAt);
        when(notification.getWeatherCondition())
                .thenReturn(WeatherCondition.HEAVY_RAIN);
        when(notification.getTargetType())
                .thenReturn(NotificationTargetType.TRIP);
        when(notification.getTargetId()).thenReturn(1001L);
        when(repository.findUndeliveredCreatedBetween(
                LocalDateTime.of(2026, 10, 16, 0, 0),
                LocalDateTime.of(2026, 10, 17, 0, 0),
                List.of(
                        NotificationType.TRIP_D1,
                        NotificationType.TRIP_WEATHER_ALERT
                )
        )).thenReturn(List.of(notification));

        assertThat(service.findTodayUndelivered()).containsExactly(
                new NotificationPublishEvent(
                        recipientPublicId,
                        16001L,
                        NotificationType.TRIP_WEATHER_ALERT,
                        WeatherCondition.HEAVY_RAIN,
                        "여행지에 특이 기상이 예상돼요",
                        "여행 기간의 날씨를 확인해 주세요.",
                        new NotificationTargetResponse(
                                NotificationTargetType.TRIP,
                                "1001"
                        ),
                        createdAt.atOffset(java.time.ZoneOffset.ofHours(9))
                )
        );
        verify(repository).findUndeliveredCreatedBetween(
                LocalDateTime.of(2026, 10, 16, 0, 0),
                LocalDateTime.of(2026, 10, 17, 0, 0),
                List.of(
                        NotificationType.TRIP_D1,
                        NotificationType.TRIP_WEATHER_ALERT
                )
        );
    }
}
