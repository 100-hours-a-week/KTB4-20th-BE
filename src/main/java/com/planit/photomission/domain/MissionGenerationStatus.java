package com.planit.photomission.domain;

public enum MissionGenerationStatus {
    /** 메시지 큐로 접수해 AI 결과를 기다리는 중 */
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED
}
