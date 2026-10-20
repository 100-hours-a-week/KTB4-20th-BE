package com.planit.notification.dto;

public record UnreadNotificationCountResponse(
        long unreadCount,
        boolean hasUnread
) {
}
