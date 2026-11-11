package com.planit.mq.result;

import com.planit.mq.config.RabbitMqConfig;
import com.planit.mq.config.RabbitMqProperties;
import com.planit.mq.topology.AiExchange;
import com.planit.mq.topology.AiResultQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 처리하지 못한 결과 메시지를 Result Retry Queue나 응답 DLQ로 다시 발행한다.
 * 호출한 쪽은 confirm을 받았을 때만 원본을 ACK하고, 아니면 원본을 큐로 되돌린다.
 */
@Component
public class AiResultRepublisher {

    public static final String RESULT_RETRY_COUNT_HEADER = "x-result-retry-count";
    public static final String ERROR_KIND_HEADER = "x-error-kind";
    public static final String ERROR_MESSAGE_HEADER = "x-error-message";
    public static final String FAILED_AT_HEADER = "x-failed-at";

    /** Result Retry Queue 단계 수(1s, 2s, 4s). 모두 쓰면 응답 DLQ로 보낸다. */
    static final int MAX_RESULT_RETRIES = AiResultQueue.RETRY_TTL_MILLIS.size();
    static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    private static final Logger log = LoggerFactory.getLogger(AiResultRepublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqProperties properties;
    private final Clock clock;

    public AiResultRepublisher(
            RabbitTemplate rabbitTemplate,
            RabbitMqProperties properties,
            @Qualifier(RabbitMqConfig.MQ_CLOCK) Clock clock
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 결과 반영 중 일시 오류가 나면 부른다. `x-result-retry-count`가 0~2면 다음 Result Retry Queue로,
     * 3이면 응답 DLQ로 보낸다.
     *
     * @return confirm을 받았으면 true
     */
    public boolean retryOrDeadLetter(Message original, Throwable error) {
        int retryCount = resultRetryCountOf(original);
        if (retryCount >= MAX_RESULT_RETRIES) {
            return deadLetter(original, AiResultErrorKind.RESULT_PROCESSING_FAILED, describe(error));
        }
        int stage = retryCount + 1;
        MessageProperties retryProperties = copyProperties(original);
        retryProperties.setHeader(RESULT_RETRY_COUNT_HEADER, stage);
        return publish(AiExchange.RETRY, AiResultQueue.retryRoutingKey(stage), original, retryProperties);
    }

    /**
     * 다시 처리해도 같은 결과인 메시지를 응답 DLQ로 보낸다.
     *
     * @return confirm을 받았으면 true
     */
    public boolean deadLetter(Message original, AiResultErrorKind errorKind, String errorMessage) {
        MessageProperties dlqProperties = copyProperties(original);
        dlqProperties.setHeader(ERROR_KIND_HEADER, errorKind.name());
        dlqProperties.setHeader(ERROR_MESSAGE_HEADER, truncate(errorMessage));
        dlqProperties.setHeader(FAILED_AT_HEADER,
                OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS).toString());
        return publish(AiExchange.DLX, AiResultQueue.DLQ_ROUTING_KEY, original, dlqProperties);
    }

    /** 헤더가 없거나 숫자가 아니면 0으로 본다. */
    static int resultRetryCountOf(Message message) {
        Object value = message.getMessageProperties().getHeaders().get(RESULT_RETRY_COUNT_HEADER);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private boolean publish(String exchange, String routingKey, Message original, MessageProperties messageProperties) {
        CorrelationData correlationData = new CorrelationData();
        try {
            rabbitTemplate.send(exchange, routingKey, new Message(original.getBody(), messageProperties), correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (confirm.ack() && correlationData.getReturned() == null) {
                return true;
            }
            log.warn("결과 메시지 재발행 실패 exchange={}, routingKey={}, ack={}, returned={}, reason={}",
                    exchange, routingKey, confirm.ack(), correlationData.getReturned(), confirm.reason());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("결과 메시지 재발행 confirm 대기 중단 exchange={}, routingKey={}", exchange, routingKey, e);
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            log.warn("결과 메시지 재발행 실패 exchange={}, routingKey={}", exchange, routingKey, e);
            return false;
        }
    }

    /**
     * 원본의 메시지 ID, 작업 ID, 종류 등 속성과 헤더를 그대로 옮긴다.
     * RabbitMQ가 dead-letter할 때 붙인 x-death 계열 헤더는 브로커가 관리하므로 옮기지 않는다.
     */
    private static MessageProperties copyProperties(Message original) {
        MessageProperties source = original.getMessageProperties();
        MessageProperties copy = new MessageProperties();
        copy.setContentType(source.getContentType());
        copy.setContentEncoding(source.getContentEncoding());
        copy.setDeliveryMode(source.getDeliveryMode());
        copy.setMessageId(source.getMessageId());
        copy.setCorrelationId(source.getCorrelationId());
        copy.setType(source.getType());
        copy.setTimestamp(source.getTimestamp());
        copy.setAppId(source.getAppId());
        for (Map.Entry<String, Object> header : source.getHeaders().entrySet()) {
            if (!isBrokerDeathHeader(header.getKey())) {
                copy.setHeader(header.getKey(), header.getValue());
            }
        }
        return copy;
    }

    private static boolean isBrokerDeathHeader(String name) {
        return name.equals("x-death") || name.startsWith("x-first-death-") || name.startsWith("x-last-death-");
    }

    private static String describe(Throwable error) {
        return error.getClass().getSimpleName() + ": " + error.getMessage();
    }

    private static String truncate(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH ? message : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
