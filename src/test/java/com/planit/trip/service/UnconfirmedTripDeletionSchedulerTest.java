package com.planit.trip.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnconfirmedTripDeletionSchedulerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @DisplayName("서울 시각 매일 23:50:00에 실행되고 다중 인스턴스 중 하나만 실행한다")
    @Test
    void runsAtSeoul2350Daily() throws NoSuchMethodException {
        Method method = UnconfirmedTripDeletionScheduler.class.getMethod(
                "deleteUnconfirmedTrips"
        );
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
        CronExpression cron = CronExpression.parse(scheduled.cron());
        assertThat(cron.next(ZonedDateTime.of(
                2026, 10, 18, 23, 49, 59, 0, SEOUL
        ))).isEqualTo(ZonedDateTime.of(2026, 10, 18, 23, 50, 0, 0, SEOUL));
        assertThat(cron.next(ZonedDateTime.of(
                2026, 10, 18, 23, 50, 0, 0, SEOUL
        ))).isEqualTo(ZonedDateTime.of(2026, 10, 19, 23, 50, 0, 0, SEOUL));
        assertThat(method.getAnnotation(SchedulerLock.class)).isNotNull();
    }

    @DisplayName("서울 시각 기준 다음 날 시작하는 여행을 실행 시각으로 삭제한다")
    @Test
    void deletesTripsStartingNextSeoulDate() {
        UnconfirmedTripDeletionService service =
                mock(UnconfirmedTripDeletionService.class);
        // UTC로는 10월 18일 14:50이지만 서울 시각은 10월 18일 23:50이다.
        Clock clock = Clock.fixed(Instant.parse("2026-10-18T14:50:00Z"), SEOUL);
        LocalDate nextDate = LocalDate.of(2026, 10, 19);
        LocalDateTime now = LocalDateTime.of(2026, 10, 18, 23, 50);
        when(service.findTargetTripIds(nextDate)).thenReturn(List.of(1L));

        new UnconfirmedTripDeletionScheduler(service, clock)
                .deleteUnconfirmedTrips();

        verify(service).deleteIfUnconfirmed(1L, nextDate, now);
    }

    @DisplayName("한 여행 삭제가 실패해도 나머지 여행 삭제를 계속한다")
    @Test
    void continuesWhenOneTripFails() {
        UnconfirmedTripDeletionService service =
                mock(UnconfirmedTripDeletionService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-18T14:50:00Z"), SEOUL);
        LocalDate nextDate = LocalDate.of(2026, 10, 19);
        LocalDateTime now = LocalDateTime.of(2026, 10, 18, 23, 50);
        when(service.findTargetTripIds(nextDate)).thenReturn(List.of(1L, 2L));
        when(service.deleteIfUnconfirmed(1L, nextDate, now))
                .thenThrow(new IllegalStateException("lock timeout"));

        new UnconfirmedTripDeletionScheduler(service, clock)
                .deleteUnconfirmedTrips();

        verify(service).deleteIfUnconfirmed(2L, nextDate, now);
    }
}
