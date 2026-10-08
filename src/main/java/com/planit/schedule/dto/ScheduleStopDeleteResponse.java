package com.planit.schedule.dto;

public record ScheduleStopDeleteResponse(
        String scheduleId,
        String deletedStopId,
        ScheduleDetailResponse.Day day
) {
}
