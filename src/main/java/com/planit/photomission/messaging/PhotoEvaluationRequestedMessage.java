package com.planit.photomission.messaging;

import java.time.LocalDateTime;

public record PhotoEvaluationRequestedMessage(
        int schemaVersion,
        Long evaluationId,
        Long missionPhotoId,
        LocalDateTime deadlineAt
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public static PhotoEvaluationRequestedMessage create(
            Long evaluationId,
            Long missionPhotoId,
            LocalDateTime deadlineAt
    ) {
        return new PhotoEvaluationRequestedMessage(
                CURRENT_SCHEMA_VERSION,
                evaluationId,
                missionPhotoId,
                deadlineAt
        );
    }
}
