package com.planit.schedule.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ScheduleConfirmRequest(
        @NotNull @Positive Long scheduleId
) {
}
