package com.planit.notification.precheck;

import com.planit.domain.Trip;
import com.planit.domain.TripMemberRole;
import com.planit.domain.User;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.domain.WeatherCondition;

import java.util.Objects;

/**
 * 여행 D-1 알림 한 건을 만들기 위한 대상이다.
 * weatherCondition은 이상 기후 알림에서 인식 가능한 날씨 사유가 있을 때만 존재하고,
 * hostName은 일반 멤버에게 방장이 결정 중임을 알릴 때 사용한다.
 */
public record TripRemindNotificationTarget(
        Trip trip,
        User recipient,
        TripMemberRole recipientRole,
        NotificationType type,
        WeatherCondition weatherCondition,
        String hostName
) {

    public TripRemindNotificationTarget {
        Objects.requireNonNull(trip, "알림 대상 여행은 필수입니다");
        Objects.requireNonNull(recipient, "알림 수신자는 필수입니다");
        Objects.requireNonNull(recipientRole, "알림 수신자 역할은 필수입니다");
        Objects.requireNonNull(type, "알림 유형은 필수입니다");
    }
}
