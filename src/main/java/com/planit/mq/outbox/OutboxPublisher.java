package com.planit.mq.outbox;

import com.planit.mq.config.RabbitMqConfig;
import com.planit.mq.config.RabbitMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 메시지를 RabbitMQ로 발행하고, confirm 결과는 기다리지 않고 {@link OutboxPublishResultHandler}에 넘긴다.
 * 발행에 실패해도 Outbox 행이 PENDING으로 남아 있으므로 Relay가 다시 발행한다.
 */
@Component
public class OutboxPublisher {

    public static final String RETRY_COUNT_HEADER = "x-retry-count";
    static final String APP_ID = "planit-backend";

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final MessageOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxPublishResultHandler resultHandler;
    private final RabbitMqProperties properties;
    private final ExecutorService confirmExecutor;
    private final Clock clock;

    public OutboxPublisher(
            MessageOutboxRepository repository,
            RabbitTemplate rabbitTemplate,
            OutboxPublishResultHandler resultHandler,
            RabbitMqProperties properties,
            @Qualifier(RabbitMqConfig.CONFIRM_EXECUTOR) ExecutorService confirmExecutor,
            @Qualifier(RabbitMqConfig.MQ_CLOCK) Clock clock
    ) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.resultHandler = resultHandler;
        this.properties = properties;
        this.confirmExecutor = confirmExecutor;
        this.clock = clock;
    }

    /**
     * 커밋 직후 호출한다. 브로커 연결이 늦어져도 요청 스레드가 막히지 않도록 발행 자체도 별도 실행기에서 한다.
     * 예외는 로그만 남긴다.
     */
    public void publishAsync(Long outboxId) {
        try {
            confirmExecutor.execute(() -> {
                try {
                    repository.findById(outboxId)
                            .filter(outbox -> outbox.getStatus() == MessageOutboxStatus.PENDING)
                            .ifPresent(this::publish);
                } catch (RuntimeException e) {
                    log.warn("커밋 직후 메시지 발행 실패, Relay가 다시 발행함 outboxId={}", outboxId, e);
                }
            });
        } catch (RuntimeException e) {
            log.warn("커밋 직후 메시지 발행을 시작하지 못함, Relay가 다시 발행함 outboxId={}", outboxId, e);
        }
    }

    /**
     * 메시지를 발행하고 confirm을 기다리지 않고 돌아온다.
     * confirm, 시간 초과, 발행 예외는 모두 {@link OutboxPublishResultHandler}가 Outbox에 반영한다.
     *
     * @return 발행 결과를 Outbox에 반영하고 나면 끝나는 Future. confirm 대기시간이 지나면 시간 초과로 반영하므로
     * 그보다 오래 걸리지 않는다. 반영 중 예외는 로그만 남기고 Future는 정상으로 끝난다.
     */
    public CompletableFuture<Void> publish(MessageOutbox outbox) {
        CorrelationData correlationData = new CorrelationData(String.valueOf(outbox.getId()));
        try {
            rabbitTemplate.send(
                    outbox.getExchangeName(),
                    outbox.getRoutingKey(),
                    toMessage(outbox, clock.instant()),
                    correlationData
            );
        } catch (RuntimeException e) {
            handleSendFailure(outbox.getId(), e);
            return CompletableFuture.completedFuture(null);
        }
        return correlationData.getFuture()
                .orTimeout(properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .handleAsync(
                        (confirm, error) -> {
                            handleConfirm(outbox.getId(), correlationData, confirm, error);
                            return null;
                        },
                        confirmExecutor
                );
    }

    private void handleSendFailure(Long outboxId, RuntimeException error) {
        try {
            resultHandler.handleSendFailure(outboxId, error);
        } catch (RuntimeException e) {
            log.error("발행 예외 반영 실패, Relay가 다시 발행함 outboxId={}", outboxId, e);
        }
    }

    private void handleConfirm(
            Long outboxId,
            CorrelationData correlationData,
            CorrelationData.Confirm confirm,
            Throwable error
    ) {
        try {
            resultHandler.handleConfirm(outboxId, correlationData, confirm, error);
        } catch (RuntimeException e) {
            log.error("발행 결과 반영 실패, Relay가 다시 발행함 outboxId={}", outboxId, e);
        }
    }

    static Message toMessage(MessageOutbox outbox, Instant publishedAt) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MediaType.APPLICATION_JSON_VALUE);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(outbox.getMessageId().toString());
        properties.setCorrelationId(String.valueOf(outbox.getJobId()));
        properties.setType(outbox.getJobType().name());
        // AMQP timestamp는 초 단위로만 전달된다
        properties.setTimestamp(Date.from(publishedAt.truncatedTo(ChronoUnit.SECONDS)));
        properties.setAppId(APP_ID);
        properties.setHeader(RETRY_COUNT_HEADER, 0);
        return new Message(outbox.getPayload().getBytes(StandardCharsets.UTF_8), properties);
    }
}
