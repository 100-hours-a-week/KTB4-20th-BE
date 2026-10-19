package com.planit.schedule.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleChangeDeadlineTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 10, 20);

    @DisplayName("여행 시작 전날 23시 50분 전까지만 일정 변경을 허용한다")
    @Test
    void allowsOnlyBeforeDeadline() {
        assertThat(ScheduleChangeDeadline.isAllowed(
                START_DATE,
                LocalDateTime.of(2026, 10, 19, 23, 49, 59)
        )).isTrue();
        assertThat(ScheduleChangeDeadline.isAllowed(
                START_DATE,
                LocalDateTime.of(2026, 10, 19, 23, 50)
        )).isFalse();
    }
}
