package com.planit.schedule.controller;

import com.planit.global.response.ApiResponse;
import com.planit.schedule.dto.ScheduleDetailResponse;
import com.planit.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class ScheduleController {

    private static final String SUCCESS_CODE = "ACTIVE_SCHEDULE_RETRIEVED";
    private static final String SUCCESS_MESSAGE = "확정 일정을 조회했습니다.";

    private final ScheduleService scheduleService;

    @GetMapping("/{tripId}/schedule")
    public ApiResponse<ScheduleDetailResponse> getSchedule(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        ScheduleDetailResponse response = scheduleService.getSchedule(
                authentication.getName(),
                tripId
        );

        return ApiResponse.success(
                SUCCESS_CODE,
                SUCCESS_MESSAGE,
                response
        );
    }
}
