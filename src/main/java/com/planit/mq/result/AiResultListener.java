package com.planit.mq.result;

import com.planit.mq.outbox.OutboxPublisher;
import com.planit.mq.topology.AiResultQueue;
import com.rabbitmq.client.Channel;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * 응답 큐의 결과 메시지를 읽어 작업 종류별 결과 Handler에 넘긴다.
 * Handler가 결과를 커밋하고 돌아오면 ACK한다. 읽을 수 없는 메시지와 Handler가 없는 작업 종류는
 * 다시 처리해도 같으므로 바로 응답 DLQ로 보내고, Handler에서 난 예외는 Result Retry Queue로 다시 보낸다.
 * 재발행 confirm을 받지 못하면 원본을 큐로 되돌려 다시 받는다. 되돌린 횟수가 큐의 재전달 한도를 넘으면
 * RabbitMQ가 응답 DLQ로 보낸다.
 */
@Component
public class AiResultListener {

    private static final Logger log = LoggerFactory.getLogger(AiResultListener.class);

    private final AiResultEnvelopeParser parser;
    private final AiResultHandlers handlers;
    private final AiResultRepublisher republisher;

    public AiResultListener(
            AiResultEnvelopeParser parser,
            AiResultHandlers handlers,
            AiResultRepublisher republisher
    ) {
        this.parser = parser;
        this.handlers = handlers;
        this.republisher = republisher;
    }

    @RabbitListener(
            queues = AiResultQueue.TASK_QUEUE,
            containerFactory = AiResultListenerConfig.RESULT_LISTENER_FACTORY
    )
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        if (process(message)) {
            channel.basicAck(deliveryTag, false);
        } else {
            channel.basicNack(deliveryTag, false, true);
        }
    }

    /** @return 원본을 ACK해도 되면 true, 큐로 되돌려야 하면 false */
    boolean process(Message message) {
        AiResultEnvelope result;
        try {
            result = parser.parse(message.getBody());
        } catch (InvalidAiResultMessageException e) {
            warn("읽을 수 없는 AI 결과 메시지를 응답 DLQ로 보냄 messageId=%s, reason=%s"
                    .formatted(message.getMessageProperties().getMessageId(), e.getMessage()));
            return republisher.deadLetter(message, AiResultErrorKind.INVALID_MESSAGE, e.getMessage());
        }

        Optional<AiResultHandler> handler = handlers.find(result.jobType());
        if (handler.isEmpty()) {
            String reason = "결과 Handler가 없는 작업 종류 jobType=" + result.jobType();
            warn("%s, jobId=%s를 응답 DLQ로 보냄".formatted(reason, result.jobId()));
            return republisher.deadLetter(message, AiResultErrorKind.UNSUPPORTED_JOB_TYPE, reason);
        }

        try {
            handler.get().handle(result, AiResultRepublisher.countHeaderOf(message, OutboxPublisher.RETRY_COUNT_HEADER));
            return true;
        } catch (RuntimeException e) {
            return retryOrDeadLetter(message, result, e);
        }
    }

    private boolean retryOrDeadLetter(Message message, AiResultEnvelope result, RuntimeException error) {
        int resultRetryCount = AiResultRepublisher.resultRetryCountOf(message);
        if (resultRetryCount >= AiResultRepublisher.MAX_RESULT_RETRIES) {
            log.error("AI 결과 반영 재시도를 모두 써서 응답 DLQ로 보냄 jobType={}, jobId={}",
                    result.jobType(), result.jobId(), error);
            Sentry.captureException(error);
        } else {
            log.warn("AI 결과 반영 실패로 다시 시도함 jobType={}, jobId={}, resultRetryCount={}",
                    result.jobType(), result.jobId(), resultRetryCount, error);
        }
        return republisher.retryOrDeadLetter(message, error);
    }

    private static void warn(String message) {
        log.warn(message);
        Sentry.captureMessage(message, SentryLevel.WARNING);
    }
}
