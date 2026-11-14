package com.planit.mq.result;

import com.planit.mq.job.AiJobType;

/**
 * 작업 종류별 AI 결과 반영. Result Listener가 결과 메시지의 jobType에 맞는 Handler를 골라 부른다.
 * 작업 상태 조건부 UPDATE가 1행일 때만 결과와 알림을 저장하므로 같은 결과가 여러 번 와도 한 번만 반영된다.
 * 트랜잭션은 Handler가 열고 커밋까지 끝낸 뒤 돌아오고, Listener는 돌아온 뒤에 ACK한다.
 * 이미 끝난 작업의 결과처럼 반영할 것이 없으면 예외 없이 돌아온다.
 * 예외를 던지면 일시 오류로 보고 Result Retry Queue로 다시 보낸다.
 */
public interface AiResultHandler {

    AiJobType jobType();

    /**
     * @param retryCount 결과 메시지 헤더 `x-retry-count`. AI 처리 실패로 재시도한 횟수이고, 없으면 0이다.
     */
    void handle(AiResultEnvelope result, int retryCount);
}
