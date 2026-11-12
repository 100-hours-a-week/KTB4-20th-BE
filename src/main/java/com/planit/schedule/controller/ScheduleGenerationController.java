package com.planit.schedule.controller;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.global.response.ApiResponse;
import com.planit.mq.intake.AiJobAcceptedResponse;
import com.planit.mq.intake.AiJobIntakeGuard;
import com.planit.mq.job.AiJobType;
import com.planit.schedule.domain.ScheduleGenerationReason;
import com.planit.schedule.dto.ScheduleGenerationRequest;
import com.planit.schedule.dto.SchedulePlaceSelectionResponse;
import com.planit.schedule.service.ScheduleGenerationIntakeService;
import com.planit.schedule.service.ScheduleGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class ScheduleGenerationController {

    private static final String SUCCESS_CODE =
            "SCHEDULE_PLACE_SELECTION_COMPLETED";
    private static final String SUCCESS_MESSAGE =
            "AI가 여행 장소를 선정했습니다.";
    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final ScheduleGenerationService scheduleGenerationService;
    private final ScheduleGenerationIntakeService intakeService;
    private final AiJobIntakeGuard intakeGuard;

    /**
     * 전환 플래그가 켜져 있으면 메시지 큐로 접수하고 202를, 꺼져 있으면 기존처럼 AI를 동기 호출하고 200을 반환한다.
     * 비동기 접수에서 Idempotency-Key 헤더는 UUID여야 하고, 같은 키로 다시 요청하면 처음 접수한 작업을 돌려준다.
     */
    @PostMapping("/{tripId}/schedule-generation")
    public ResponseEntity<?> generate(
            Authentication authentication,
            @PathVariable Long tripId,
            @RequestBody(required = false) ScheduleGenerationRequest request,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey
    ) {
        ScheduleGenerationReason reason = request == null
                ? ScheduleGenerationReason.GENERAL
                : request.reasonOrDefault();

        if (intakeGuard.isAsync(AiJobType.SCHEDULE_GENERATE)) {
            Long jobId = intakeService.accept(
                    authentication.getName(),
                    tripId,
                    reason,
                    parseIdempotencyKey(idempotencyKey)
            );
            return AiJobAcceptedResponse.accepted(AiJobType.SCHEDULE_GENERATE, jobId);
        }

        SchedulePlaceSelectionResponse response =
                scheduleGenerationService.generate(
                        authentication.getName(),
                        tripId,
                        reason
                );
        return ResponseEntity.ok(ApiResponse.success(
                SUCCESS_CODE,
                SUCCESS_MESSAGE,
                response
        ));
    }

    private static UUID parseIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        try {
            return UUID.fromString(idempotencyKey);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, e);
        }
    }
}
