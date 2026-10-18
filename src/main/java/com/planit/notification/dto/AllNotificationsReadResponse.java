package com.planit.notification.dto;

import java.time.OffsetDateTime;

public record AllNotificationsReadResponse(
        int updatedCount,
        OffsetDateTime readAt,
        long unreadCount
) {
}
