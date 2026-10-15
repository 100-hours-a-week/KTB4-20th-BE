package com.planit.notification.precheck;

import com.planit.domain.Trip;
import com.planit.domain.User;
import com.planit.notification.domain.NotificationType;

import java.util.Objects;

public record TripRemindNotificationTarget(
        Trip trip,
        User recipient,
        NotificationType type
) {

    public TripRemindNotificationTarget {
        Objects.requireNonNull(trip, "알림 대상 여행은 필수입니다");
        Objects.requireNonNull(recipient, "알림 수신자는 필수입니다");
        Objects.requireNonNull(type, "알림 유형은 필수입니다");
    }
}
