package com.planit.notification.dto;

import com.planit.notification.domain.NotificationType;

import java.time.OffsetDateTime;
import java.util.List;

public record PendingNotificationDeliveriesResponse(
        List<Item> items
) {
    public record Item(
            String notificationId,
            NotificationType type,
            String title,
            String body,
            OffsetDateTime createdAt
    ) {
    }
}
