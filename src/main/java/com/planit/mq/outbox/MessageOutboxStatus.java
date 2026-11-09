package com.planit.mq.outbox;

public enum MessageOutboxStatus {
    /** 발행 전이거나 confirm을 아직 받지 못함. Relay의 재발행 대상이다. */
    PENDING,
    /** ack를 받고 라우팅까지 확인됨 */
    PUBLISHED,
    /** return을 받았거나 재시도 한도를 넘겨 발행을 포기함 */
    FAILED
}
