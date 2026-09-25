package com.planit.schedule.service;

public record GeneratedScheduleResult(
        String scheduleId,
        String dayId,
        long totalDistanceMeters,
        int placeCount,
        int legCount
) {
}
