package com.planit.photomission.service;

import java.time.LocalDateTime;

public record PhotoEvaluationFailedResult(
        Long evaluationId,
        String errorCode,
        LocalDateTime failedAt
) {

    public PhotoEvaluationFailedResult {
        if (evaluationId == null) {
            throw new IllegalArgumentException("AI 판정 작업 ID는 필수입니다");
        }
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("AI 실행 오류 코드는 필수입니다");
        }
        if (failedAt == null) {
            throw new IllegalArgumentException("AI 판정 실패 시각은 필수입니다");
        }
    }
}
