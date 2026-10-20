package com.planit.photomission.ai;

import com.planit.photomission.domain.PhotoEvaluation;
import com.planit.photomission.domain.PhotoEvaluationReason;
import com.planit.photomission.domain.PhotoEvaluationResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Objects;

@Component
public class PhotoMissionEvaluationMapper {

    private final ObjectMapper objectMapper;

    public PhotoMissionEvaluationMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void apply(
            PhotoEvaluation evaluation,
            PhotoMissionVerifyResponse response,
            LocalDateTime evaluatedAt
    ) {
        Objects.requireNonNull(response.detectedLabels());

        evaluation.succeed(
                mapResult(response.result()),
                mapReason(response.reason()),
                response.matchScore(),
                objectMapper.writeValueAsString(response.detectedLabels()),
                response.landmarkConfidence(),
                response.retryHint(),
                evaluatedAt
        );
    }

    private PhotoEvaluationResult mapResult(String result) {
        return switch (result) {
            case "success" -> PhotoEvaluationResult.SUCCESS;
            case "retry" -> PhotoEvaluationResult.RETRY;
            case "fail" -> PhotoEvaluationResult.FAIL;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 AI 판정 결과입니다: " + result
            );
        };
    }

    private PhotoEvaluationReason mapReason(String reason) {
        if (reason == null) {
            return null;
        }
        if ("location_mismatch".equals(reason)) {
            return PhotoEvaluationReason.LOCATION_MISMATCH;
        }
        throw new IllegalArgumentException(
                "지원하지 않는 AI 판정 사유입니다: " + reason
        );
    }
}
