package com.planit.notification.delivery;

import com.planit.notification.domain.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationWebSocketSenderTest {

    @DisplayName("내부 알림 이벤트를 수신 사용자 전용 WebSocket 응답으로 전송한다")
    @Test
    void sendsNotificationToRecipientWithoutInternalRoutingField() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        NotificationWebSocketSender sender = new NotificationWebSocketSender(
                messagingTemplate
        );
        UUID recipientPublicId = UUID.randomUUID();
        NotificationPublishEvent event = new NotificationPublishEvent(
                recipientPublicId,
                16001L,
                NotificationType.TRIP_WEATHER_ALERT,
                "여행지에 특이 기상이 예상돼요",
                "여행 기간의 날씨를 확인해 주세요.",
                OffsetDateTime.parse("2026-10-16T10:00:00+09:00")
        );

        sender.send(event);

        ArgumentCaptor<NotificationWebSocketResponse> response =
                ArgumentCaptor.forClass(NotificationWebSocketResponse.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(recipientPublicId.toString()),
                eq("/queue/notifications"),
                response.capture()
        );
        assertThat(response.getValue()).isEqualTo(
                new NotificationWebSocketResponse(
                        event.notificationId(),
                        event.notificationType(),
                        event.title(),
                        event.body(),
                        event.createdAt()
                )
        );
    }
}
