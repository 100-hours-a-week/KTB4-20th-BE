package com.planit.photomission.domain;

import com.planit.domain.Trip;
import com.planit.schedule.domain.Schedule;
import com.planit.schedule.domain.ScheduleDay;
import com.planit.schedule.domain.ScheduleVisit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MissionTest {

    @DisplayName("개인·단체 공통 미션 정보와 마감 시각을 생성한다")
    @Test
    void createsMission() {
        Trip trip = mock(Trip.class);
        ScheduleDay scheduleDay = mock(ScheduleDay.class);
        ScheduleVisit scheduleVisit = visit(trip, scheduleDay);
        LocalDateTime now =
                LocalDateTime.of(2026, 10, 7, 15, 0);
        org.mockito.Mockito.when(trip.getEndDate())
                .thenReturn(LocalDate.of(2026, 10, 8));

        Mission mission = Mission.create(
                scheduleVisit,
                1,
                MissionScope.GROUP,
                "다 같이 단체 사진 찍기",
                "HISTORY_CULTURE",
                now
        );

        assertThat(mission.getTrip()).isSameAs(trip);
        assertThat(mission.getScheduleDay()).isSameAs(scheduleDay);
        assertThat(mission.getScheduleVisit()).isSameAs(scheduleVisit);
        assertThat(mission.getMissionOrder()).isEqualTo((byte) 1);
        assertThat(mission.getScope()).isEqualTo(MissionScope.GROUP);
        assertThat(mission.getDescription())
                .isEqualTo("다 같이 단체 사진 찍기");
        assertThat(mission.getPrimaryCategory())
                .isEqualTo("HISTORY_CULTURE");
        assertThat(mission.getExpiresAt())
                .isEqualTo(LocalDateTime.of(2026, 10, 9, 0, 0));
        assertThat(mission.getCreatedAt()).isEqualTo(now);
    }

    @DisplayName("마감 시각 전에는 수행 가능하고 마감 시각부터 만료된다")
    @Test
    void determinesExpirationAtBoundary() {
        Mission mission = createMission();

        assertThat(mission.isExpired(
                LocalDateTime.of(2026, 10, 8, 23, 59, 59)
        )).isFalse();
        assertThat(mission.isExpired(
                LocalDateTime.of(2026, 10, 9, 0, 0)
        )).isTrue();
    }

    @DisplayName("신규 미션은 대상 방문 장소가 반드시 필요하다")
    @Test
    void requiresScheduleVisit() {
        Trip trip = mock(Trip.class);
        org.mockito.Mockito.when(trip.getEndDate())
                .thenReturn(LocalDate.of(2026, 10, 8));

        assertThatThrownBy(() -> Mission.create(
                null,
                1,
                MissionScope.PERSONAL,
                "유적지 전경 담기",
                "HISTORY_CULTURE",
                LocalDateTime.of(2026, 10, 7, 15, 0)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("미션 대상 방문 장소는 필수입니다");
    }

    private Mission createMission() {
        Trip trip = mock(Trip.class);
        org.mockito.Mockito.when(trip.getEndDate())
                .thenReturn(LocalDate.of(2026, 10, 8));
        ScheduleDay scheduleDay = mock(ScheduleDay.class);
        return Mission.create(
                visit(trip, scheduleDay),
                1,
                MissionScope.PERSONAL,
                "유적지 전경 담기",
                null,
                LocalDateTime.of(2026, 10, 7, 15, 0)
        );
    }

    private ScheduleVisit visit(Trip trip, ScheduleDay scheduleDay) {
        Schedule schedule = mock(Schedule.class);
        ScheduleVisit scheduleVisit = mock(ScheduleVisit.class);
        when(scheduleVisit.getDay()).thenReturn(scheduleDay);
        when(scheduleDay.getSchedule()).thenReturn(schedule);
        when(schedule.getTrip()).thenReturn(trip);
        return scheduleVisit;
    }
}
