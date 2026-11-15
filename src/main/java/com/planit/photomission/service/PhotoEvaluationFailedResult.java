package com.planit.photomission.service;

import java.time.LocalDateTime;

public record PhotoEvaluationFailedResult(
        Long evaluationId,
        int executionAttemptCount,
        String errorCode,
        LocalDateTime failedAt
) {

    private static final int MAX_EXECUTION_ATTEMPT_COUNT = 4;

    public PhotoEvaluationFailedResult {
        if (evaluationId == null) {
            throw new IllegalArgumentException("AI 판정 작업 ID는 필수입니다");
        }
        if (executionAttemptCount < 1
                || executionAttemptCount > MAX_EXECUTION_ATTEMPT_COUNT) {
            throw new IllegalArgumentException(
                    "AI 판정 실행 차수는 1부터 4까지입니다"
            );
        }
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("AI 실행 오류 코드는 필수입니다");
        }
        if (failedAt == null) {
            throw new IllegalArgumentException("AI 판정 실패 시각은 필수입니다");
        }
    }
}
