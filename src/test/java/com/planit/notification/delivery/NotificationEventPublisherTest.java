package com.planit.notification.delivery;

import com.planit.notification.domain.NotificationType;
import com.planit.notification.domain.NotificationTargetType;
import com.planit.notification.domain.WeatherCondition;
import com.planit.notification.dto.NotificationTargetResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationEventPublisherTest {

    @DisplayName("내부 알림 이벤트를 JSON으로 직렬화해 고정 채널로 발행한다")
    @Test
    void publishesSerializedEventToNotificationChannel() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = JsonMapper.builder().build();
        NotificationEventPublisher publisher = new NotificationEventPublisher(
                redisTemplate,
                objectMapper
        );
        UUID recipientPublicId = UUID.randomUUID();
        NotificationPublishEvent event = new NotificationPublishEvent(
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
                OffsetDateTime.parse("2026-10-16T10:00:00+09:00")
        );

        publisher.publish(event);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(
                eq("planit:notifications"),
                payload.capture()
        );
        JsonNode json = objectMapper.readTree(payload.getValue());
        assertThat(json.get("recipientPublicId").stringValue())
                .isEqualTo(recipientPublicId.toString());
        assertThat(json.get("notificationId").longValue()).isEqualTo(16001L);
        assertThat(json.get("notificationType").stringValue())
                .isEqualTo("TRIP_WEATHER_ALERT");
        assertThat(json.get("title").stringValue())
                .isEqualTo("여행지에 특이 기상이 예상돼요");
        assertThat(json.get("body").stringValue())
                .isEqualTo("여행 기간의 날씨를 확인해 주세요.");
        assertThat(json.get("weatherCondition").stringValue())
                .isEqualTo("HEAVY_RAIN");
        assertThat(json.get("target").get("id").stringValue())
                .isEqualTo("1001");
        assertThat(json.get("createdAt").stringValue())
                .isEqualTo("2026-10-16T10:00:00+09:00");
    }
}
