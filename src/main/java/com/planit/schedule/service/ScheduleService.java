package com.planit.schedule.service;

import com.planit.schedule.dto.ScheduleDetailResponse;

public interface ScheduleService {

    ScheduleDetailResponse getSchedule(
            String userPublicId,
            Long tripId
    );
}
