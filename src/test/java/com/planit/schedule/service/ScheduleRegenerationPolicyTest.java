package com.planit.schedule.service;

import com.planit.domain.Trip;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.notification.domain.NotificationTargetType;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.repository.NotificationRepository;
import com.planit.schedule.domain.ScheduleGenerationReason;
import com.planit.schedule.repository.ScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduleRegenerationPolicyTest {

    private static final long TRIP_ID = 100L;
    // 서울 기준 2026-10-17 10:00
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-17T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private static final LocalDate START_DATE = LocalDate.of(2026, 10, 18);

    private ScheduleRepository scheduleRepository;
    private NotificationRepository notificationRepository;
    private ScheduleRegenerationPolicy policy;
    private Trip trip;

    @BeforeEach
    void setUp() {
        scheduleRepository = mock(ScheduleRepository.class);
        notificationRepository = mock(NotificationRepository.class);
        policy = new ScheduleRegenerationPolicy(
                scheduleRepository,
                notificationRepository,
                CLOCK
        );
        trip = mock(Trip.class);
        when(trip.getId()).thenReturn(TRIP_ID);
        when(trip.getStartDate()).thenReturn(START_DATE);
    }

    @DisplayName("확정 일정이 없으면 최초 생성이다")
    @Test
    void resolvesInitialWithoutSchedule() {
        assertThat(policy.resolve(trip, ScheduleGenerationReason.GENERAL))
                .isEqualTo(ScheduleGenerationMode.INITIAL);
    }

    @DisplayName("확정 일정이 있고 횟수가 남았으면 일반 재생성이다")
    @Test
    void resolvesGeneralRegeneration() {
        givenSchedule();
        when(trip.getScheduleRegenerationCount()).thenReturn(4);

        assertThat(policy.resolve(trip, ScheduleGenerationReason.GENERAL))
                .isEqualTo(ScheduleGenerationMode.GENERAL_REGENERATION);
    }

    @DisplayName("일반 재생성 5회를 모두 사용하면 거부한다")
    @Test
    void rejectsGeneralRegenerationOverLimit() {
        givenSchedule();
        when(trip.getScheduleRegenerationCount()).thenReturn(5);

        assertError(
                ScheduleGenerationReason.GENERAL,
                ErrorCode.SCHEDULE_REGENERATION_LIMIT_EXCEEDED
        );
    }

    @DisplayName("여행 시작일부터는 재생성을 거부한다")
    @Test
    void rejectsRegenerationOnTripStartDate() {
        givenSchedule();
        when(trip.getStartDate()).thenReturn(LocalDate.of(2026, 10, 17));

        assertError(
                ScheduleGenerationReason.GENERAL,
                ErrorCode.SCHEDULE_CHANGE_NOT_ALLOWED
        );
    }

    @DisplayName("이상 기후 알림을 받은 여행은 횟수와 관계없이 날씨 재생성할 수 있다")
    @Test
    void resolvesWeatherRegenerationRegardlessOfCount() {
        givenSchedule();
        givenWeatherAlert(true);
        when(trip.getScheduleRegenerationCount()).thenReturn(5);

        assertThat(policy.resolve(trip, ScheduleGenerationReason.WEATHER))
                .isEqualTo(ScheduleGenerationMode.WEATHER_REGENERATION);
    }

    @DisplayName("이상 기후 알림이 없으면 날씨 재생성을 거부한다")
    @Test
    void rejectsWeatherRegenerationWithoutAlert() {
        givenSchedule();
        givenWeatherAlert(false);

        assertError(
                ScheduleGenerationReason.WEATHER,
                ErrorCode.WEATHER_REPLAN_NOT_AVAILABLE
        );
    }

    @DisplayName("대체할 확정 일정이 없으면 날씨 재생성을 거부한다")
    @Test
    void rejectsWeatherRegenerationWithoutSchedule() {
        givenWeatherAlert(true);

        assertError(
                ScheduleGenerationReason.WEATHER,
                ErrorCode.WEATHER_REPLAN_NOT_AVAILABLE
        );
    }

    @DisplayName("남은 재생성 횟수는 0 미만으로 내려가지 않는다")
    @Test
    void calculatesRemainingRegenerations() {
        when(trip.getScheduleRegenerationCount()).thenReturn(2);
        assertThat(ScheduleRegenerationPolicy.remainingRegenerations(trip))
                .isEqualTo(3);

        when(trip.getScheduleRegenerationCount()).thenReturn(7);
        assertThat(ScheduleRegenerationPolicy.remainingRegenerations(trip))
                .isZero();
    }

    private void givenSchedule() {
        when(scheduleRepository.existsByTripIdAndActiveConfirmedSlot(
                TRIP_ID,
                (byte) 1
        )).thenReturn(true);
    }

    private void givenWeatherAlert(boolean exists) {
        when(notificationRepository
                .existsByTargetTypeAndTargetIdAndTypeAndCreatedAtGreaterThanEqualAndDeletedAtIsNull(
                        NotificationTargetType.TRIP,
                        TRIP_ID,
                        NotificationType.TRIP_WEATHER_ALERT,
                        LocalDateTime.of(2026, 10, 17, 0, 0)
                )).thenReturn(exists);
    }

    private void assertError(
            ScheduleGenerationReason reason,
            ErrorCode expected
    ) {
        assertThatThrownBy(() -> policy.resolve(trip, reason))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expected)
                );
    }
}
