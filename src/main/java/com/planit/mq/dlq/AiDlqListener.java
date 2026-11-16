package com.planit.mq.dlq;

import com.planit.mq.job.AiJobTerminationService;
import com.planit.mq.job.AiJobType;
import com.rabbitmq.client.Channel;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 요청 DLQ로 온 메시지의 작업을 실패로 끝내고 실패 알림을 저장한다.
 * 작업은 본문이 아니라 `correlation_id`(작업 ID)와 `type`(작업 종류) 속성으로 찾는다. 본문을 읽을 수 없는 메시지도 처리하기 위해서다.
 * 실패 원인은 `x-error-kind` 헤더 값이고, 없으면 RabbitMQ가 붙인 `x-death` 사유로 정한다.
 * 이미 끝난 작업이면 알림 없이 ACK한다. 처리 중 예외가 나면 큐로 되돌려 다시 받는다.
 * 포토미션 판정 DLQ는 판정 처리 쪽에서, 포토미션 생성 DLQ는 05시 재처리에서 다룬다.
 */
@Component
public class AiDlqListener {

    public static final String ERROR_KIND_HEADER = "x-error-kind";
    /** 재전달 한도를 넘어 RabbitMQ가 dead-letter한 경우 */
    public static final String DELIVERY_LIMIT = "DELIVERY_LIMIT";
    /** 실패 원인 헤더도 재전달 한도 초과 기록도 없는 경우 */
    public static final String DEAD_LETTERED = "DEAD_LETTERED";

    /** 작업 테이블 error_code 컬럼 길이 */
    static final int MAX_ERROR_CODE_LENGTH = 100;

    private static final String DELIVERY_LIMIT_REASON = "delivery_limit";
    private static final int MAX_LOGGED_BODY_LENGTH = 200;
    private static final Logger log = LoggerFactory.getLogger(AiDlqListener.class);

    private final AiJobTerminationService terminationService;
    private final TransactionTemplate transaction;

    public AiDlqListener(AiJobTerminationService terminationService, PlatformTransactionManager transactionManager) {
        this.terminationService = terminationService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @RabbitListener(
            queues = "#{T(com.planit.mq.topology.AiJobQueue).SCHEDULE_GENERATE.dlq()}",
            containerFactory = AiDlqListenerConfig.DLQ_LISTENER_FACTORY
    )
    public void onScheduleGenerationDlq(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            process(message, AiJobType.SCHEDULE_GENERATE);
        } catch (RuntimeException e) {
            log.error("요청 DLQ 메시지 처리 실패, 다시 받음 messageId={}",
                    message.getMessageProperties().getMessageId(), e);
            channel.basicNack(deliveryTag, false, true);
            return;
        }
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 작업을 알 수 없는 메시지는 다시 처리해도 같으므로 Sentry에 남기고 처리한 것으로 본다.
     * 그 작업은 Stale Job Checker가 기준 시간이 지나면 실패로 끝낸다.
     */
    void process(Message message, AiJobType queueJobType) {
        MessageProperties properties = message.getMessageProperties();
        Optional<Long> jobId = jobIdOf(properties);
        if (jobId.isEmpty() || !queueJobType.name().equals(properties.getType())) {
            String description = "요청 DLQ 메시지의 작업을 알 수 없어 버림 queue=%s, messageId=%s, correlationId=%s, type=%s, body=%s"
                    .formatted(properties.getConsumerQueue(), properties.getMessageId(), properties.getCorrelationId(),
                            properties.getType(), bodyPreview(message));
            log.error(description);
            Sentry.captureMessage(description, SentryLevel.ERROR);
            return;
        }

        String errorCode = errorCodeOf(properties);
        boolean failed = Boolean.TRUE.equals(
                transaction.execute(status -> terminationService.fail(queueJobType, jobId.get(), errorCode)));
        if (failed) {
            log.warn("요청 DLQ로 온 작업을 실패 처리함 jobType={}, jobId={}, errorCode={}",
                    queueJobType, jobId.get(), errorCode);
        }
    }

    /** `x-error-kind`가 있으면 그 값, 없고 마지막 dead-letter 사유가 재전달 한도 초과면 DELIVERY_LIMIT이다. */
    public static String errorCodeOf(MessageProperties properties) {
        Object errorKind = properties.getHeaders().get(ERROR_KIND_HEADER);
        if (errorKind != null && !errorKind.toString().isBlank()) {
            return truncate(errorKind.toString());
        }
        List<Map<String, ?>> deaths = properties.getXDeathHeader();
        if (deaths != null && !deaths.isEmpty()
                && DELIVERY_LIMIT_REASON.equals(String.valueOf(deaths.getFirst().get("reason")))) {
            return DELIVERY_LIMIT;
        }
        return DEAD_LETTERED;
    }

    private static Optional<Long> jobIdOf(MessageProperties properties) {
        try {
            return Optional.of(Long.valueOf(properties.getCorrelationId()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String truncate(String value) {
        return value.length() <= MAX_ERROR_CODE_LENGTH ? value : value.substring(0, MAX_ERROR_CODE_LENGTH);
    }

    private static String bodyPreview(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        return body.length() <= MAX_LOGGED_BODY_LENGTH ? body : body.substring(0, MAX_LOGGED_BODY_LENGTH);
    }
}
