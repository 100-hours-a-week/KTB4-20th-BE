package com.planit.schedule.dto;

public record ScheduleStopAddResponse(
        String scheduleId,
        ScheduleDetailResponse.Day day
) {
}
