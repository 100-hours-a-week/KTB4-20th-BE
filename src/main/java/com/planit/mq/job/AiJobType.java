package com.planit.mq.job;

/** 메시지 큐로 처리하는 AI 작업 종류. Outbox `job_type`, 메시지 `type` 속성과 같은 값이다. */
public enum AiJobType {
    SCHEDULE_GENERATE,
    PHOTO_MISSION_GENERATE,
    PHOTO_JUDGE
}
