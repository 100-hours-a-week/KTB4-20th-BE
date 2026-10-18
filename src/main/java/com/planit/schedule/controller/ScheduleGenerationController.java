package com.planit.schedule.controller;

import com.planit.global.response.ApiResponse;
import com.planit.schedule.domain.ScheduleGenerationReason;
import com.planit.schedule.dto.ScheduleGenerationRequest;
import com.planit.schedule.dto.SchedulePlaceSelectionResponse;
import com.planit.schedule.service.ScheduleGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class ScheduleGenerationController {

    private static final String SUCCESS_CODE =
            "SCHEDULE_PLACE_SELECTION_COMPLETED";
    private static final String SUCCESS_MESSAGE =
            "AI가 여행 장소를 선정했습니다.";

    private final ScheduleGenerationService scheduleGenerationService;

    @PostMapping("/{tripId}/schedule-generation")
    public ApiResponse<SchedulePlaceSelectionResponse> generate(
            Authentication authentication,
            @PathVariable Long tripId,
            @RequestBody(required = false) ScheduleGenerationRequest request
    ) {
        SchedulePlaceSelectionResponse response =
                scheduleGenerationService.generate(
                        authentication.getName(),
                        tripId,
                        request == null
                                ? ScheduleGenerationReason.GENERAL
                                : request.reasonOrDefault()
                );

        return ApiResponse.success(
                SUCCESS_CODE,
                SUCCESS_MESSAGE,
                response
        );
    }
}
