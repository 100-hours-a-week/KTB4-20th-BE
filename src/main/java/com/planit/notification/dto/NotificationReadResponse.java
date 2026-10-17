package com.planit.notification.dto;

import com.planit.notification.domain.NotificationTargetStatus;

import java.time.OffsetDateTime;

public record NotificationReadResponse(
        String notificationId,
        boolean read,
        OffsetDateTime readAt,
        NotificationTargetStatus targetStatus,
        boolean navigable
) {
}
