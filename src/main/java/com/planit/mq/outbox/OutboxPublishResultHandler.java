package com.planit.mq.outbox;

import com.planit.mq.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

/**
 * 발행 결과(publisher confirm, return, 예외)를 Outbox에 반영한다. 즉시 발행과 Relay가 같이 쓴다.
 * - ack, return 없음: PUBLISHED
 * - ack + return: 재시도 없이 실패 처리
 * - nack: NACK 횟수 + 1, 한도를 넘으면 실패 처리
 * - confirm 시간 초과, 발행 예외: 유실 횟수 + 1, 한도를 넘으면 실패 처리
 */
@Component
public class OutboxPublishResultHandler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublishResultHandler.class);
    private static final int LAST_ERROR_MAX_LENGTH = 500;

    private final MessageOutboxRepository repository;
    private final OutboxRetryPolicy retryPolicy;
    private final OutboxFailureService failureService;
    private final Clock clock;

    public OutboxPublishResultHandler(
            MessageOutboxRepository repository,
            OutboxRetryPolicy retryPolicy,
            OutboxFailureService failureService,
            @Qualifier(RabbitMqConfig.MQ_CLOCK) Clock clock
    ) {
        this.repository = repository;
        this.retryPolicy = retryPolicy;
        this.failureService = failureService;
        this.clock = clock;
    }

    /**
     * confirm Future가 끝났을 때 호출한다.
     *
     * @param confirm 브로커의 confirm. 시간 초과나 연결 예외로 끝났으면 null
     * @param error   Future가 예외로 끝났을 때의 예외. confirm을 받았으면 null
     */
    @Transactional
    public void handleConfirm(Long outboxId, CorrelationData correlationData, CorrelationData.Confirm confirm, Throwable error) {
        if (error != null) {
            handleLoss(outboxId, unwrap(error));
            return;
        }
        if (!confirm.ack()) {
            handleNack(outboxId, confirm.reason());
            return;
        }
        ReturnedMessage returned = correlationData.getReturned();
        if (returned != null) {
            failureService.fail(
                    outboxId,
                    PublishFailureCode.PUBLISH_RETURNED,
                    lastError("RETURNED", returned.getReplyCode() + " " + returned.getReplyText())
            );
            return;
        }
        repository.markPublished(outboxId, now());
    }

    /** `RabbitTemplate.send()`가 예외를 던져 confirm을 기다릴 수도 없을 때 호출한다. */
    @Transactional
    public void handleSendFailure(Long outboxId, Throwable error) {
        handleLoss(outboxId, unwrap(error));
    }

    private void handleNack(Long outboxId, String reason) {
        MessageOutbox outbox = findPending(outboxId);
        if (outbox == null) {
            return;
        }
        int attempt = outbox.getNackAttemptCount() + 1;
        String lastError = lastError("NACK", reason);
        if (retryPolicy.isNackExhausted(attempt)) {
            failureService.fail(outboxId, PublishFailureCode.PUBLISH_FAILED, lastError);
            return;
        }
        log.warn("메시지 발행 NACK, 재시도 예정 outboxId={}, attempt={}, reason={}", outboxId, attempt, reason);
        repository.scheduleNackRetry(outboxId, now().plus(retryPolicy.backoff(attempt)), lastError);
    }

    private void handleLoss(Long outboxId, Throwable error) {
        MessageOutbox outbox = findPending(outboxId);
        if (outbox == null) {
            return;
        }
        int attempt = outbox.getFailureAttemptCount() + 1;
        String lastError = error instanceof TimeoutException
                ? lastError("TIMEOUT", "confirm not received")
                : lastError("SEND_ERROR", error.getClass().getSimpleName() + ": " + error.getMessage());
        if (retryPolicy.isFailureExhausted(attempt)) {
            failureService.fail(outboxId, PublishFailureCode.PUBLISH_FAILED, lastError);
            return;
        }
        log.warn("메시지 발행 결과를 받지 못함, 재시도 예정 outboxId={}, attempt={}, lastError={}",
                outboxId, attempt, lastError);
        repository.scheduleFailureRetry(outboxId, now().plus(retryPolicy.backoff(attempt)), lastError);
    }

    /** 다른 경로가 이미 끝낸 메시지면 null. 이후 조건부 UPDATE도 같은 조건이라 여기서 미리 걸러도 결과는 같다. */
    private MessageOutbox findPending(Long outboxId) {
        return repository.findById(outboxId)
                .filter(outbox -> outbox.getStatus() == MessageOutboxStatus.PENDING)
                .orElse(null);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String lastError(String kind, String detail) {
        String message = kind + ": " + detail;
        return message.length() <= LAST_ERROR_MAX_LENGTH ? message : message.substring(0, LAST_ERROR_MAX_LENGTH);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
