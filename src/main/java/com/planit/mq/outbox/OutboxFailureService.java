package com.planit.mq.outbox;

import com.planit.mq.job.AiJobPublishFailureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 발행을 포기한 메시지의 Outbox와 작업을 한 트랜잭션에서 실패로 끝낸다. */
@Service
public class OutboxFailureService {

    private static final Logger log = LoggerFactory.getLogger(OutboxFailureService.class);

    private final MessageOutboxRepository repository;
    private final ObjectProvider<AiJobPublishFailureListener> listeners;

    public OutboxFailureService(
            MessageOutboxRepository repository,
            ObjectProvider<AiJobPublishFailureListener> listeners
    ) {
        this.repository = repository;
        this.listeners = listeners;
    }

    /**
     * @return Outbox를 이번 호출에서 FAILED로 바꿨으면 true. 다른 경로가 먼저 끝냈으면 false이고 작업은 건드리지 않는다.
     */
    @Transactional
    public boolean fail(Long outboxId, PublishFailureCode failureCode, String lastError) {
        if (repository.markFailed(outboxId, lastError) == 0) {
            return false;
        }
        MessageOutbox outbox = repository.findById(outboxId).orElseThrow();
        log.warn("AI 작업 메시지 발행을 포기함 outboxId={}, jobType={}, jobId={}, failureCode={}, lastError={}",
                outboxId, outbox.getJobType(), outbox.getJobId(), failureCode, lastError);
        listeners.orderedStream()
                .forEach(listener -> listener.onPublishFailed(outbox.getJobType(), outbox.getJobId(), failureCode));
        return true;
    }
}
