package com.planit.schedule.service;

import com.planit.schedule.dto.ScheduleDetailResponse;
import com.planit.schedule.dto.ScheduleStopDeleteResponse;
import com.planit.schedule.dto.ScheduleStopAddRequest;
import com.planit.schedule.dto.ScheduleStopAddResponse;

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

    ScheduleStopAddResponse addStop(
            String userPublicId,
            Long tripId,
            ScheduleStopAddRequest request
    );
}
