package com.planit.schedule.dto;

import java.util.List;

public record ScheduleCandidateDetailResponse(
        String tripId,
        String scheduleId,
        String strategy,
        String summary,
        Integer preferenceScore,
        int totalDistanceMeters,
        boolean editable,
        List<ScheduleDetailResponse.Day> days
) {
    public ScheduleCandidateDetailResponse {
        days = List.copyOf(days);
    }
}
