package com.planit.photomission.service;

public record QueuedSubmission(
        Long photoId,
        Long evaluationId
) {
}
