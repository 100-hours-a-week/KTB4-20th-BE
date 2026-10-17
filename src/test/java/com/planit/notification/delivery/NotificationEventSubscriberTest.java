package com.planit.notification.delivery;

import com.planit.notification.domain.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.Message;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEventSubscriberTest {

    @DisplayName("Redis JSON 메시지를 내부 알림 이벤트로 전달한다")
    @Test
    void deserializesAndPublishesApplicationEvent() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        ApplicationEventPublisher applicationEventPublisher =
                mock(ApplicationEventPublisher.class);
        NotificationEventSubscriber subscriber =
                new NotificationEventSubscriber(
                        objectMapper,
                        applicationEventPublisher
                );
        UUID recipientPublicId = UUID.randomUUID();
        NotificationPublishEvent expected = new NotificationPublishEvent(
                recipientPublicId,
                16001L,
                NotificationType.TRIP_WEATHER_ALERT,
                "여행지에 특이 기상이 예상돼요",
                "여행 기간의 날씨를 확인해 주세요.",
                OffsetDateTime.parse("2026-10-16T10:00:00+09:00")
        );
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(
                objectMapper.writeValueAsString(expected)
                        .getBytes(StandardCharsets.UTF_8)
        );

        subscriber.onMessage(message, null);

        ArgumentCaptor<NotificationPublishEvent> event =
                ArgumentCaptor.forClass(NotificationPublishEvent.class);
        verify(applicationEventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(expected);
    }
}
