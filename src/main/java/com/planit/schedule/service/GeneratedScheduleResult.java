package com.planit.schedule.service;

import java.util.List;

public record GeneratedScheduleResult(
        String scheduleId,
        List<String> dayIds,
        long totalDistanceMeters,
        int placeCount,
        int legCount
) {
    public GeneratedScheduleResult {
        dayIds = List.copyOf(dayIds);
    }
}
