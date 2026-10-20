package com.planit.schedule.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

final class ScheduleChangeDeadline {

    private ScheduleChangeDeadline() {
    }

    static boolean isAllowed(LocalDate tripStartDate, LocalDateTime now) {
        return now.isBefore(tripStartDate.atStartOfDay().minusMinutes(10));
    }
}
