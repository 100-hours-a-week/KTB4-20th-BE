package com.planit.notification.delivery;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.domain.WeatherCondition;
import com.planit.notification.dto.NotificationTargetResponse;

import java.time.OffsetDateTime;

public record NotificationWebSocketResponse(
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

    public static NotificationWebSocketResponse from(
            NotificationPublishEvent event
    ) {
        return new NotificationWebSocketResponse(
                event.notificationId(),
                event.notificationType(),
                event.weatherCondition(),
                event.title(),
                event.body(),
                event.target(),
                event.createdAt()
        );
    }
}
