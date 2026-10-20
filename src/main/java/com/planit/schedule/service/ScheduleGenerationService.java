package com.planit.schedule.service;

import com.planit.schedule.domain.ScheduleGenerationReason;
import com.planit.schedule.dto.SchedulePlaceSelectionResponse;

public interface ScheduleGenerationService {

    SchedulePlaceSelectionResponse generate(
            String userPublicId,
            Long tripId,
            ScheduleGenerationReason reason
    );
}
