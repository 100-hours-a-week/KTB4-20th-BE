package com.planit.mq.intake;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;

import java.time.Duration;
import java.util.Objects;

/**
 * 큐가 막혀 AI 작업을 접수하지 않을 때 던진다. 작업과 Outbox는 저장하지 않는다.
 * 응답에는 다시 시도할 때까지 기다릴 시간을 `Retry-After` 헤더로 붙인다.
 */
public class AiJobTemporarilyUnavailableException extends BusinessException {

    private final Duration retryAfter;

    public AiJobTemporarilyUnavailableException(Duration retryAfter) {
        super(ErrorCode.AI_JOB_TEMPORARILY_UNAVAILABLE);
        this.retryAfter = Objects.requireNonNull(retryAfter, "재시도 대기 시간은 필수입니다.");
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
