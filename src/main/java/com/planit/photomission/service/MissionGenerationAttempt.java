package com.planit.photomission.service;

public record MissionGenerationAttempt(
        Long jobId,
        int attemptCount
) {
}
