package com.planit.mq.outbox;

/** 발행을 포기한 이유. 작업 테이블 `error_code`에 그대로 저장한다. */
public enum PublishFailureCode {
    /** NACK 또는 confirm 유실·연결 예외가 재시도 한도를 넘음 */
    PUBLISH_FAILED,
    /** 브로커가 메시지를 큐로 보내지 못하고 돌려보냄 */
    PUBLISH_RETURNED
}
