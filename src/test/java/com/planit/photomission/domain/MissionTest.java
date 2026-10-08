package com.planit.photomission.domain;

import com.planit.domain.Trip;
import com.planit.schedule.domain.ScheduleDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MissionTest {

    @DisplayName("개인·단체 공통 미션 정보와 마감 시각을 생성한다")
    @Test
    void createsMission() {
        Trip trip = mock(Trip.class);
        ScheduleDay scheduleDay = mock(ScheduleDay.class);
        LocalDateTime now =
                LocalDateTime.of(2026, 10, 7, 15, 0);
        org.mockito.Mockito.when(trip.getEndDate())
                .thenReturn(LocalDate.of(2026, 10, 8));

        Mission mission = Mission.create(
                10L,
                trip,
                scheduleDay,
                1,
                MissionScope.GROUP,
                "다 같이 단체 사진 찍기",
                "멤버 모두 나오게 한 장에 담아주세요",
                "{\"requiredLabels\":[\"사람\"]}",
                now
        );

        assertThat(mission.getMissionGenerationJobId()).isEqualTo(10L);
        assertThat(mission.getTrip()).isSameAs(trip);
        assertThat(mission.getScheduleDay()).isSameAs(scheduleDay);
        assertThat(mission.getMissionOrder()).isEqualTo((byte) 1);
        assertThat(mission.getScope()).isEqualTo(MissionScope.GROUP);
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

    private Mission createMission() {
        Trip trip = mock(Trip.class);
        org.mockito.Mockito.when(trip.getEndDate())
                .thenReturn(LocalDate.of(2026, 10, 8));
        return Mission.create(
                10L,
                trip,
                mock(ScheduleDay.class),
                1,
                MissionScope.PERSONAL,
                "유적지 전경 담기",
                "화면에 크게 담기도록 찍어주세요",
                "{}",
                LocalDateTime.of(2026, 10, 7, 15, 0)
        );
    }
}
