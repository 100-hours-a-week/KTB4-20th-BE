package com.planit.notification.delivery;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.domain.WeatherCondition;
import com.planit.notification.dto.NotificationTargetResponse;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

public record NotificationPublishEvent(
        UUID recipientPublicId,
        Long notificationId,
        NotificationType notificationType,
        WeatherCondition weatherCondition,
        String title,
        String body,
        NotificationTargetResponse target,
        @JsonFormat(
                without = JsonFormat.Feature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE
        )
        OffsetDateTime createdAt
) {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    public static NotificationPublishEvent from(Notification notification) {
        Objects.requireNonNull(notification, "발행할 알림은 필수입니다");

        return new NotificationPublishEvent(
                notification.getRecipient().getPublicId(),
                notification.getId(),
                notification.getType(),
                notification.getWeatherCondition(),
                notification.getTitle(),
                notification.getBody(),
                NotificationTargetResponse.from(notification),
                notification.getCreatedAt()
                        .atZone(SEOUL_ZONE)
                        .toOffsetDateTime()
        );
    }
}
