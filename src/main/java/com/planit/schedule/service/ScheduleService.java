package com.planit.schedule.service;

import com.planit.schedule.dto.ScheduleDetailResponse;
import com.planit.schedule.dto.ScheduleStopDeleteResponse;

public interface ScheduleService {

    ScheduleDetailResponse getSchedule(
            String userPublicId,
            Long tripId
    );

    ScheduleStopDeleteResponse deleteStop(
            String userPublicId,
            Long tripId,
            Long stopId
    );
}
