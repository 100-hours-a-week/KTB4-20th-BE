package com.planit.schedule.dto;

import java.time.OffsetDateTime;

public record ScheduleConfirmResponse(
        String tripId,
        String scheduleId,
        String strategy,
        String status,
        OffsetDateTime confirmedAt
) {
}
