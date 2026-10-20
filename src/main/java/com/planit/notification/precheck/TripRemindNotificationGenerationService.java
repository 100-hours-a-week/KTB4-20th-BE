package com.planit.notification.precheck;

import com.planit.domain.TripMemberRole;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.domain.WeatherCondition;
import com.planit.notification.repository.NotificationStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class TripRemindNotificationGenerationService {

    private static final String D1_TITLE_FORMAT = "%s 여행이 내일 시작돼요";
    private static final String D1_BODY = "D-1, 준비물과 일정을 미리 확인해보세요.";
    private static final String WEATHER_TITLE_FORMAT = "내일 %s 예상돼요";
    private static final String WEATHER_HOST_BODY_FORMAT =
            "D-1, %s 지역에 %s 예보되었어요. 일정을 변경할까요?";
    private static final String WEATHER_MEMBER_BODY_FORMAT =
            "%s님이 일정 변경 여부를 결정하고 있어요.";
    private static final String UNKNOWN_WEATHER_LABEL = "특이 기상";
    private static final String UNKNOWN_HOST_NAME = "방장";

    private final TripRemindNotificationTargetService targetService;
    private final NotificationStore notificationStore;
    private final Clock clock;

    public TripRemindNotificationGenerationService(
            TripRemindNotificationTargetService targetService,
            NotificationStore notificationStore,
            @Qualifier("tripRemindClock") Clock clock
    ) {
        this.targetService = targetService;
        this.notificationStore = notificationStore;
        this.clock = clock;
    }

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
        String regionName = target.trip().getRegion().getName();

        return Notification.createTripNotification(
                target.recipient(),
                target.trip().getId(),
                target.trip().getStartDate(),
                target.type(),
                weatherAlert ? target.weatherCondition() : null,
                weatherAlert
                        ? weatherTitle(target.weatherCondition())
                        : D1_TITLE_FORMAT.formatted(regionName),
                weatherAlert
                        ? weatherBody(target, regionName)
                        : D1_BODY,
                createdAt
        );
    }

    private String weatherTitle(WeatherCondition condition) {
        return WEATHER_TITLE_FORMAT.formatted(withSubjectParticle(
                weatherLabel(condition)
        ));
    }

    private String weatherBody(
            TripRemindNotificationTarget target,
            String regionName
    ) {
        if (target.recipientRole() == TripMemberRole.HOST) {
            return WEATHER_HOST_BODY_FORMAT.formatted(
                    regionName,
                    withSubjectParticle(weatherLabel(target.weatherCondition()))
            );
        }
        String hostName = target.hostName() == null
                || target.hostName().isBlank()
                ? UNKNOWN_HOST_NAME
                : target.hostName();
        return WEATHER_MEMBER_BODY_FORMAT.formatted(hostName);
    }

    private String weatherLabel(WeatherCondition condition) {
        return condition == null ? UNKNOWN_WEATHER_LABEL : condition.getLabel();
    }

    /** 마지막 글자의 받침 여부에 따라 주격 조사 "이"/"가"를 붙인다. */
    static String withSubjectParticle(String word) {
        char last = word.charAt(word.length() - 1);
        boolean hangul = last >= '가' && last <= '힣';
        boolean hasFinalConsonant = hangul && (last - '가') % 28 != 0;
        return word + (hasFinalConsonant ? "이" : "가");
    }
}
