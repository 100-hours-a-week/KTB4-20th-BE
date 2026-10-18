package com.planit.notification.dto;

import com.planit.notification.domain.NotificationTargetStatus;
import com.planit.notification.domain.NotificationTargetType;
import com.planit.notification.domain.NotificationType;

import java.time.OffsetDateTime;
import java.util.List;

public record NotificationListResponse(
        List<Item> items,
        Page page
) {
    public record Item(
            String notificationId,
            NotificationType type,
            String title,
            String body,
            Target target,
            boolean read,
            OffsetDateTime readAt,
            OffsetDateTime createdAt
    ) {
    }

    public record Target(
            NotificationTargetType type,
            String id,
            NotificationTargetStatus status
    ) {
    }

    public record Page(String nextCursor, boolean hasNext) {
    }
}
