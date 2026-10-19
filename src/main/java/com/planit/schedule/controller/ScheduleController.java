package com.planit.schedule.controller;

import com.planit.global.response.ApiResponse;
import com.planit.schedule.dto.ScheduleCandidateDetailResponse;
import com.planit.schedule.dto.ScheduleCandidateListResponse;
import com.planit.schedule.dto.ScheduleConfirmRequest;
import com.planit.schedule.dto.ScheduleConfirmResponse;
import com.planit.schedule.dto.ScheduleDetailResponse;
import com.planit.schedule.dto.ScheduleStopDeleteResponse;
import com.planit.schedule.dto.ScheduleStopAddRequest;
import com.planit.schedule.dto.ScheduleStopAddResponse;
import com.planit.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class ScheduleController {

    private static final String SUCCESS_CODE = "ACTIVE_SCHEDULE_RETRIEVED";
    private static final String SUCCESS_MESSAGE = "확정 일정을 조회했습니다.";

    private final ScheduleService scheduleService;

    @PutMapping("/{tripId}/schedule")
    public ApiResponse<ScheduleConfirmResponse> confirmSchedule(
            Authentication authentication,
            @PathVariable Long tripId,
            @Valid @RequestBody ScheduleConfirmRequest request
    ) {
        return ApiResponse.success(
                "SCHEDULE_CONFIRMED",
                "일정을 확정했습니다.",
                scheduleService.confirmSchedule(
                        authentication.getName(),
                        tripId,
                        request
                )
        );
    }

    @GetMapping("/{tripId}/schedule-candidates")
    public ApiResponse<ScheduleCandidateListResponse> getScheduleCandidates(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        return ApiResponse.success(
                "SCHEDULE_CANDIDATES_RETRIEVED",
                "후보 일정을 조회했습니다.",
                scheduleService.getScheduleCandidates(
                        authentication.getName(),
                        tripId
                )
        );
    }

    @GetMapping("/{tripId}/schedule-candidates/{scheduleId}")
    public ApiResponse<ScheduleCandidateDetailResponse> getScheduleCandidate(
            Authentication authentication,
            @PathVariable Long tripId,
            @PathVariable Long scheduleId
    ) {
        return ApiResponse.success(
                "SCHEDULE_CANDIDATE_RETRIEVED",
                "후보 일정 상세를 조회했습니다.",
                scheduleService.getScheduleCandidate(
                        authentication.getName(),
                        tripId,
                        scheduleId
                )
        );
    }

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

    @DeleteMapping("/{tripId}/schedule/stops/{stopId}")
    public ApiResponse<ScheduleStopDeleteResponse> deleteStop(
            Authentication authentication,
            @PathVariable Long tripId,
            @PathVariable Long stopId
    ) {
        return ApiResponse.success(
                "SCHEDULE_STOP_DELETED",
                "일정에서 장소를 삭제했습니다.",
                scheduleService.deleteStop(
                        authentication.getName(),
                        tripId,
                        stopId
                )
        );
    }

    @PostMapping("/{tripId}/schedule/stops")
    public ResponseEntity<ApiResponse<ScheduleStopAddResponse>> addStop(
            Authentication authentication,
            @PathVariable Long tripId,
            @Valid @RequestBody ScheduleStopAddRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(
                        "SCHEDULE_STOP_ADDED",
                        "일정에 장소를 추가했습니다.",
                        scheduleService.addStop(
                                authentication.getName(),
                                tripId,
                                request
                        )
                )
        );
    }
}
