package com.planit.schedule.domain;

public enum ScheduleGenerationJobStatus {
    /** 접수 후 AI 결과를 기다리는 중. 여행별로 하나만 있을 수 있다. */
    QUEUED,
    /** 결과를 검증하고 일정 후보까지 저장함 */
    SUCCEEDED,
    /** 발행 실패, AI 실패, 결과 검증 실패, 기준 시간 초과 중 하나로 끝남 */
    FAILED
}
