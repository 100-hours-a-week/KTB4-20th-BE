package com.planit.photomission.messaging;

import com.planit.mq.dlq.AiDlqListener;
import com.planit.mq.dlq.AiDlqListenerConfig;
import com.planit.mq.job.AiJobType;
import com.planit.mq.outbox.OutboxPublisher;
import com.planit.photomission.service.PhotoEvaluationFailureService;
import com.rabbitmq.client.Channel;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PhotoEvaluationDlqListener {

    private static final Logger log = LoggerFactory.getLogger(PhotoEvaluationDlqListener.class);

    private final PhotoEvaluationFailureService failureService;

    public PhotoEvaluationDlqListener(PhotoEvaluationFailureService failureService) {
        this.failureService = failureService;
    }

    @RabbitListener(
            queues = "#{T(com.planit.mq.topology.AiJobQueue).PHOTO_JUDGE.dlq()}",
            containerFactory = AiDlqListenerConfig.DLQ_LISTENER_FACTORY
    )
    public void onPhotoEvaluationDlq(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            process(message);
        } catch (RuntimeException exception) {
            log.error("포토미션 판정 DLQ 처리 실패, 다시 받음 messageId={}",
                    message.getMessageProperties().getMessageId(), exception);
            channel.basicNack(deliveryTag, false, true);
            return;
        }
        channel.basicAck(deliveryTag, false);
    }

    private void process(Message message) {
        MessageProperties properties = message.getMessageProperties();
        Long evaluationId = evaluationIdOf(properties);
        if (evaluationId == null || !AiJobType.PHOTO_JUDGE.name().equals(properties.getType())) {
            String description = "포토미션 판정 DLQ 메시지의 작업을 알 수 없어 버림 messageId=%s, correlationId=%s, type=%s"
                    .formatted(properties.getMessageId(), properties.getCorrelationId(), properties.getType());
            log.error(description);
            Sentry.captureMessage(description, SentryLevel.ERROR);
            return;
        }

        Integer retryCount = retryCountOf(properties);
        if (retryCount == null) {
            String description = "포토미션 판정 DLQ 메시지의 x-retry-count가 올바르지 않아 버림 messageId=%s, retryCount=%s"
                    .formatted(
                            properties.getMessageId(),
                            properties.getHeaders().get(OutboxPublisher.RETRY_COUNT_HEADER)
                    );
            log.error(description);
            Sentry.captureMessage(description, SentryLevel.ERROR);
            return;
        }
        int executionAttemptCount = retryCount + 1;
        failureService.failFromDlq(
                evaluationId,
                executionAttemptCount,
                AiDlqListener.errorCodeOf(properties)
        );
    }

    private static Long evaluationIdOf(MessageProperties properties) {
        try {
            return Long.valueOf(properties.getCorrelationId());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static Integer retryCountOf(MessageProperties properties) {
        Object value = properties.getHeaders().get(OutboxPublisher.RETRY_COUNT_HEADER);
        if (value == null) {
            return 0;
        }
        if (!(value instanceof Number number) || number.longValue() < 0 || number.longValue() > 3) {
            return null;
        }
        return number.intValue();
    }
}
