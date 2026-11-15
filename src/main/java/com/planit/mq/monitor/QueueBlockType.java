package com.planit.mq.monitor;

import com.planit.mq.job.AiJobType;

/** 새 요청을 막는 이유. 큐 감시가 Redis 차단 플래그를 켜고, AI 작업 접수가 읽는다. */
public enum QueueBlockType {

    /** 작업 큐가 거의 가득 참 */
    TASK_QUEUE("taskqueue"),
    /** 요청 DLQ가 쌓이거나 DLQ를 처리할 소비자가 없음 */
    DEAD_LETTER_QUEUE("deadletterqueue");

    private static final String KEY_FORMAT = "planit:mq:block:%s:%s";

    private final String keySegment;

    QueueBlockType(String keySegment) {
        this.keySegment = keySegment;
    }

    /** @return 차단 플래그 키 `planit:mq:block:{taskqueue|deadletterqueue}:{jobType}` */
    public String key(AiJobType jobType) {
        return KEY_FORMAT.formatted(keySegment, jobType.name());
    }
}
