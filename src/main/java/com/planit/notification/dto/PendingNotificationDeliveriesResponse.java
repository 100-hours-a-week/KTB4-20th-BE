package com.planit.notification.dto;

import com.planit.notification.domain.NotificationType;
import com.planit.notification.domain.WeatherCondition;

import java.time.OffsetDateTime;
import java.util.List;

public record PendingNotificationDeliveriesResponse(
        List<Item> items
) {
    public record Item(
            String notificationId,
            NotificationType type,
            WeatherCondition weatherCondition,
            String title,
            String body,
            NotificationTargetResponse target,
            OffsetDateTime createdAt
    ) {
    }
}
