package com.planit.photomission.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

final class PhotoMissionAvailabilityPolicy {

    private static final LocalTime DAILY_PUBLISH_TIME = LocalTime.of(0, 30);

    private PhotoMissionAvailabilityPolicy() {
    }

    static boolean isPublished(LocalDate scheduleDate, LocalDateTime now) {
        return !now.isBefore(scheduleDate.atTime(DAILY_PUBLISH_TIME));
    }
}
