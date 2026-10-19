package com.planit.schedule.service;

import com.planit.domain.Trip;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.notification.domain.NotificationTargetType;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.repository.NotificationRepository;
import com.planit.schedule.domain.ScheduleGenerationReason;
import com.planit.schedule.repository.ScheduleRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 일정 생성 요청을 최초 생성·일반 재생성·날씨 재생성 중 하나로 판정한다.
 * 일반 재생성은 여행별 최대 5회이며, 이상 기후 알림을 받은 여행의 날씨 재생성은 횟수에서 제외한다.
 * 재생성은 서울 날짜 기준 여행 시작 전까지만 허용한다.
 */
@Component
public class ScheduleRegenerationPolicy {

    public static final int MAX_GENERAL_REGENERATIONS = 5;
    private static final byte ACTIVE_CONFIRMED_SLOT = 1;

    private final ScheduleRepository scheduleRepository;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public ScheduleRegenerationPolicy(
            ScheduleRepository scheduleRepository,
            NotificationRepository notificationRepository,
            @Qualifier("tripRemindClock") Clock clock
    ) {
        this.scheduleRepository = scheduleRepository;
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    public static int remainingRegenerations(Trip trip) {
        return Math.max(
                0,
                MAX_GENERAL_REGENERATIONS - trip.getScheduleRegenerationCount()
        );
    }

    public ScheduleGenerationMode resolve(
            Trip trip,
            ScheduleGenerationReason reason
    ) {
        Objects.requireNonNull(trip, "일정 생성 대상 여행은 필수입니다");
        Objects.requireNonNull(reason, "일정 생성 사유는 필수입니다");

        boolean hasSchedule = scheduleRepository
                .existsByTripIdAndActiveConfirmedSlot(
                        trip.getId(),
                        ACTIVE_CONFIRMED_SLOT
                );

        if (reason == ScheduleGenerationReason.WEATHER) {
            if (!hasSchedule || !hasWeatherAlert(trip)) {
                throw new BusinessException(
                        ErrorCode.WEATHER_REPLAN_NOT_AVAILABLE
                );
            }
            validateBeforeTripStart(trip);
            return ScheduleGenerationMode.WEATHER_REGENERATION;
        }

        if (!hasSchedule) {
            // 후보만 만들고 확정하지 않은 상태에서는 다시 만들 수 없다. 방장이 후보 중 하나를 먼저 확정해야 한다.
            if (scheduleRepository.existsByTripIdAndStatus(trip.getId(), "DRAFT")) {
                throw new BusinessException(ErrorCode.SCHEDULE_CANDIDATES_PENDING);
            }
            return ScheduleGenerationMode.INITIAL;
        }
        validateBeforeTripStart(trip);
        if (remainingRegenerations(trip) == 0) {
            throw new BusinessException(
                    ErrorCode.SCHEDULE_REGENERATION_LIMIT_EXCEEDED
            );
        }
        return ScheduleGenerationMode.GENERAL_REGENERATION;
    }

    private void validateBeforeTripStart(Trip trip) {
        if (!LocalDate.now(clock).isBefore(trip.getStartDate())) {
            throw new BusinessException(ErrorCode.SCHEDULE_CHANGE_NOT_ALLOWED);
        }
    }

    /** 현재 여행 시작일 기준 D-1 이후에 생성된 이상 기후 알림이 있는지 확인한다. */
    private boolean hasWeatherAlert(Trip trip) {
        return notificationRepository
                .existsByTargetTypeAndTargetIdAndTypeAndCreatedAtGreaterThanEqualAndDeletedAtIsNull(
                        NotificationTargetType.TRIP,
                        trip.getId(),
                        NotificationType.TRIP_WEATHER_ALERT,
                        trip.getStartDate().minusDays(1).atStartOfDay()
                );
    }
}
