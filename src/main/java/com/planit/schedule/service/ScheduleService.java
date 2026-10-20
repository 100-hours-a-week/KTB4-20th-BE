package com.planit.schedule.service;

import com.planit.schedule.dto.ScheduleCandidateDetailResponse;
import com.planit.schedule.dto.ScheduleCandidateListResponse;
import com.planit.schedule.dto.ScheduleConfirmRequest;
import com.planit.schedule.dto.ScheduleConfirmResponse;
import com.planit.schedule.dto.ScheduleDetailResponse;
import com.planit.schedule.dto.ScheduleStopDeleteResponse;
import com.planit.schedule.dto.ScheduleStopAddRequest;
import com.planit.schedule.dto.ScheduleStopAddResponse;

public interface ScheduleService {

    ScheduleCandidateListResponse getScheduleCandidates(
            String userPublicId,
            Long tripId
    );

    ScheduleCandidateDetailResponse getScheduleCandidate(
            String userPublicId,
            Long tripId,
            Long scheduleId
    );

    ScheduleConfirmResponse confirmSchedule(
            String userPublicId,
            Long tripId,
            ScheduleConfirmRequest request
    );

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
