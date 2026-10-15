package com.planit.schedule.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ScheduleVisitTest {

    @DisplayName("일정 장소에 AI가 매칭한 여행 취향을 보존한다")
    @Test
    void preservesMatchedPreferences() {
        ScheduleVisit visit = new ScheduleVisit(
                mock(ScheduleDay.class),
                mock(Place.class),
                1,
                "역사 취향 반영",
                List.of("역사·유적", "관광·문화"),
                LocalDateTime.of(2026, 10, 14, 15, 0)
        );

        assertThat(visit.getMatchedPreferences())
                .containsExactly("역사·유적", "관광·문화");
    }
}
