package com.planit.photomission.dto;

import com.planit.photomission.domain.PhotoEvaluationExecutionStatus;

public record PhotoMissionSubmissionAcceptedResponse(
        Long photoId,
        Long evaluationId,
        PhotoEvaluationExecutionStatus status
) {
}
