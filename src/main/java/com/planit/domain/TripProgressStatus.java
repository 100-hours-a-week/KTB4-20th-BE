package com.planit.domain;

import java.time.LocalDate;

public enum TripProgressStatus {
    SURVEY_IN_PROGRESS,
    SCHEDULE_COMPLETED,
    TRIP_IN_PROGRESS,
    TRIP_COMPLETED;

    public static TripProgressStatus resolve(
            LocalDate startDate,
            boolean hasConfirmedSchedule,
            LocalDate today
    ) {
        if (startDate.isBefore(today)) {
            return TRIP_COMPLETED;
        }
        if (startDate.isEqual(today)) {
            return TRIP_IN_PROGRESS;
        }
        if (hasConfirmedSchedule) {
            return SCHEDULE_COMPLETED;
        }
        return SURVEY_IN_PROGRESS;
    }
}
