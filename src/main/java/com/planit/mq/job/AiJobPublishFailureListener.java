package com.planit.mq.job;

import com.planit.mq.outbox.PublishFailureCode;

/**
 * 메시지 발행을 포기했을 때 해당 AI 작업을 실패로 끝내는 처리.
 * Outbox를 FAILED로 바꾼 트랜잭션 안에서 호출되므로, 예외를 던지면 Outbox 실패 처리도 함께 롤백된다.
 */
public interface AiJobPublishFailureListener {

    void onPublishFailed(AiJobType jobType, Long jobId, PublishFailureCode failureCode);
}
