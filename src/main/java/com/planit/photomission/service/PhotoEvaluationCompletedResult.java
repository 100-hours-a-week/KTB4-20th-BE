package com.planit.photomission.service;

import com.planit.photomission.domain.PhotoEvaluationReason;
import com.planit.photomission.domain.PhotoEvaluationResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PhotoEvaluationCompletedResult(
        Long evaluationId,
        int executionAttemptCount,
        PhotoEvaluationResult result,
        PhotoEvaluationReason reason,
        BigDecimal matchScore,
        String detectedLabelsJson,
        BigDecimal landmarkConfidence,
        String retryHint,
        LocalDateTime evaluatedAt
) {

    private static final int MAX_EXECUTION_ATTEMPT_COUNT = 5;

    public PhotoEvaluationCompletedResult {
        if (evaluationId == null) {
            throw new IllegalArgumentException("AI 판정 작업 ID는 필수입니다");
        }
        if (executionAttemptCount < 1
                || executionAttemptCount > MAX_EXECUTION_ATTEMPT_COUNT) {
            throw new IllegalArgumentException(
                    "AI 판정 실행 차수는 1부터 5까지입니다"
            );
        }
        if (result == null) {
            throw new IllegalArgumentException("AI 판정 결과는 필수입니다");
        }
        validateScore(matchScore, "미션 수행 점수");
        if (detectedLabelsJson == null) {
            throw new IllegalArgumentException("인식 항목은 필수입니다");
        }
        if (reason != null && result != PhotoEvaluationResult.FAIL) {
            throw new IllegalArgumentException(
                    "실패 판정에만 실패 사유를 저장할 수 있습니다"
            );
        }
        if (landmarkConfidence != null) {
            validateScore(landmarkConfidence, "랜드마크 신뢰도");
        }
        if (result == PhotoEvaluationResult.RETRY
                && (retryHint == null || retryHint.isBlank())) {
            throw new IllegalArgumentException(
                    "RETRY 판정에는 재촬영 안내가 필요합니다"
            );
        }
        if (result != PhotoEvaluationResult.RETRY && retryHint != null) {
            throw new IllegalArgumentException(
                    "RETRY 판정이 아니면 재촬영 안내를 저장할 수 없습니다"
            );
        }
        if (evaluatedAt == null) {
            throw new IllegalArgumentException("AI 판정 완료 시각은 필수입니다");
        }
    }

    private static void validateScore(BigDecimal score, String fieldName) {
        if (score == null
                || score.compareTo(BigDecimal.ZERO) < 0
                || score.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(
                    fieldName + "는 0부터 100까지입니다"
            );
        }
    }
}
