package com.planit.mq.monitor;

/** 새 요청을 막는 이유. Redis 차단 플래그 키 `planit:mq:block:{keySegment}:{jobType}`에 쓴다. */
public enum QueueBlockType {

    /** 작업 큐가 거의 가득 참 */
    TASK_QUEUE("taskqueue"),
    /** 요청 DLQ가 쌓이거나 DLQ를 처리할 소비자가 없음 */
    DEAD_LETTER_QUEUE("deadletterqueue");

    private final String keySegment;

    QueueBlockType(String keySegment) {
        this.keySegment = keySegment;
    }

    public String keySegment() {
        return keySegment;
    }
}
