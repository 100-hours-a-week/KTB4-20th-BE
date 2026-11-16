package com.planit.photomission.domain;

public enum PhotoEvaluationExecutionStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    RETRY_PENDING,
    FAILED
}
