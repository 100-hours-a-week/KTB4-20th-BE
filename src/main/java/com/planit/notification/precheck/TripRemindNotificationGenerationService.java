package com.planit.notification.precheck;

import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.repository.NotificationStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TripRemindNotificationGenerationService {

    private static final String D1_TITLE = "내일 여행이 시작돼요";
    private static final String D1_BODY = "여행 일정과 날씨를 확인해 주세요.";
    private static final String WEATHER_TITLE = "여행지에 특이 기상이 예상돼요";
    private static final String WEATHER_BODY = "여행 기간의 날씨를 확인해 주세요.";

    private final TripRemindNotificationTargetService targetService;
    private final NotificationStore notificationStore;
    private final Clock clock;

    @Transactional
    public void generate(LocalDate targetDate) {
        Objects.requireNonNull(targetDate, "여행 시작 대상일은 필수입니다");
        LocalDateTime createdAt = LocalDateTime.now(clock);

        targetService.findTargets(targetDate).forEach(
                target -> notificationStore.saveIdempotently(
                        toNotification(target, createdAt)
                )
        );
    }

    private Notification toNotification(
            TripRemindNotificationTarget target,
            LocalDateTime createdAt
    ) {
        boolean weatherAlert = target.type()
                == NotificationType.TRIP_WEATHER_ALERT;

        return Notification.createTripNotification(
                target.recipient(),
                target.trip().getId(),
                target.trip().getStartDate(),
                target.type(),
                weatherAlert ? WEATHER_TITLE : D1_TITLE,
                weatherAlert ? WEATHER_BODY : D1_BODY,
                createdAt
        );
    }
}
