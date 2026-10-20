package com.planit.notification.delivery;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationWebSocketSender {

    private static final String DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void send(NotificationPublishEvent event) {
        messagingTemplate.convertAndSendToUser(
                event.recipientPublicId().toString(),
                DESTINATION,
                NotificationWebSocketResponse.from(event)
        );
    }
}
