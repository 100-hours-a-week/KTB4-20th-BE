package com.planit.notification.delivery;

import com.planit.notification.domain.NotificationType;

import java.time.OffsetDateTime;

public record NotificationWebSocketResponse(
        Long notificationId,
        NotificationType notificationType,
        String title,
        String body,
        OffsetDateTime createdAt
) {

    public static NotificationWebSocketResponse from(
            NotificationPublishEvent event
    ) {
        return new NotificationWebSocketResponse(
                event.notificationId(),
                event.notificationType(),
                event.title(),
                event.body(),
                event.createdAt()
        );
    }
}
