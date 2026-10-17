package com.planit.notification.dto;

import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationTargetType;

/** 실시간·미전달 알림에서 이동 대상을 알려준다. 당일 알림만 전달되므로 만료 상태는 포함하지 않는다. */
public record NotificationTargetResponse(
        NotificationTargetType type,
        String id
) {

    public static NotificationTargetResponse from(Notification notification) {
        if (notification.getTargetType() == null
                || notification.getTargetId() == null) {
            return null;
        }
        return new NotificationTargetResponse(
                notification.getTargetType(),
                notification.getTargetId().toString()
        );
    }
}
