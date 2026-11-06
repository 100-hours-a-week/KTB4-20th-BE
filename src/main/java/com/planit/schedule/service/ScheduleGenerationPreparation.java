package com.planit.schedule.service;

import com.planit.ai.AiPlaceSelectionRequest;

public record ScheduleGenerationPreparation(
        AiPlaceSelectionRequest request,
        ScheduleGenerationMode mode,
        long regionId,
        long tripDayCount
) {
}
