package com.planit.mq.dlq;

import com.planit.mq.config.RabbitMqProperties;
import com.planit.mq.topology.AiExchange;
import com.planit.mq.topology.AiJobQueue;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import io.sentry.Sentry;
import io.sentry.SentryLevel;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import org.springframework.amqp.rabbit.support.MessagePropertiesConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 포토미션 생성 요청 DLQ는 Consumer가 없다. 새벽 AI 장애로 DLQ에 들어간 메시지를 05:00에 한 번 더 작업 큐로 보낸다.
 * 메시지를 하나씩 꺼내 deadlineAt이 남아 있으면 x-retry-count를 4로 바꿔 다시 발행하고, confirm을 받은 뒤 DLQ에서 지운다.
 * deadlineAt이 지났거나 읽을 수 없는 메시지는 다시 보내지 않고 지운다. 그 작업은 06:00 Stale Job Checker가 실패로 끝낸다.
 * 재발행 confirm을 받지 못하면 그 메시지를 DLQ에 되돌리고 멈춘다. 작업 상태는 QUEUED 그대로 두고 바꾸지 않는다.
 */
@Component
public class PhotoMissionGenerationDlqReprocessor {

    private static final AiJobQueue QUEUE = AiJobQueue.PHOTO_MISSION_GENERATE;
    private static final int MAX_LOGGED_BODY_LENGTH = 200;
    private static final Logger log = LoggerFactory.getLogger(PhotoMissionGenerationDlqReprocessor.class);

    private final RabbitTemplate rabbitTemplate;
    private final PhotoMissionGenerationDlqMessages messages;
    private final RabbitMqProperties properties;
    private final Clock clock;
    private final MessagePropertiesConverter propertiesConverter = new DefaultMessagePropertiesConverter();

    public PhotoMissionGenerationDlqReprocessor(
            RabbitTemplate rabbitTemplate,
            PhotoMissionGenerationDlqMessages messages,
            RabbitMqProperties properties,
            @Qualifier("photoMissionClock") Clock clock
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.messages = messages;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 05:00에 한 번만 실행하므로, 반복이 60초를 넘겨 락이 풀려도 다른 인스턴스가 같은 실행 시각에 다시 시작하지 않는다.
     */
    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "photoMissionGenerationDlqReprocessor", lockAtMostFor = "PT60S")
    public void reprocess() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        try {
            Counts counts = rabbitTemplate.execute(channel -> drain(channel, now));
            log.info("포토미션 생성 DLQ 재처리 republished={}, expired={}, unreadable={}, stopped={}",
                    counts.republished, counts.expired, counts.unreadable, counts.stopped);
        } catch (RuntimeException e) {
            log.error("포토미션 생성 DLQ 재처리 중 오류", e);
            Sentry.captureException(e);
        }
    }

    private Counts drain(Channel channel, OffsetDateTime now) throws IOException {
        Counts counts = new Counts();
        GetResponse response;
        while ((response = channel.basicGet(QUEUE.dlq(), false)) != null) {
            long deliveryTag = response.getEnvelope().getDeliveryTag();
            Message dead = toMessage(response);
            Optional<OffsetDateTime> deadline = messages.deadlineOf(dead);
            boolean unreadable = deadline.isEmpty();
            boolean expired = !unreadable && !deadline.get().isAfter(now);
            boolean reprocessable = !unreadable && !expired;
            boolean republished = reprocessable && publish(PhotoMissionGenerationDlqMessages.toReprocessMessage(dead));
            boolean republishFailed = reprocessable && !republished;

            if (unreadable) {
                reportUnreadable(dead);
                channel.basicAck(deliveryTag, false);
                counts.unreadable++;
            } else if (expired) {
                channel.basicAck(deliveryTag, false);
                counts.expired++;
            } else if (republished) {
                channel.basicAck(deliveryTag, false);
                counts.republished++;
            } else if (republishFailed) {
                channel.basicNack(deliveryTag, false, true);
                counts.stopped = true;
                String warning = "포토미션 생성 DLQ 재발행 confirm을 받지 못해 재처리를 멈춤 messageId=%s, republished=%d"
                        .formatted(dead.getMessageProperties().getMessageId(), counts.republished);
                log.warn(warning);
                Sentry.captureMessage(warning, SentryLevel.WARNING);
                break;
            }
        }
        return counts;
    }

    /** confirm이 ACK이고 메시지가 큐로 가지 못해 돌아오지 않았을 때만 true */
    private boolean publish(Message message) {
        CorrelationData correlationData = new CorrelationData();
        try {
            rabbitTemplate.send(AiExchange.REQUEST, QUEUE.routingKey(), message, correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return confirm.ack() && correlationData.getReturned() == null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            log.warn("포토미션 생성 DLQ 메시지 재발행 실패 messageId={}", message.getMessageProperties().getMessageId(), e);
            return false;
        }
    }

    private Message toMessage(GetResponse response) {
        return new Message(
                response.getBody(),
                propertiesConverter.toMessageProperties(response.getProps(), response.getEnvelope(),
                        StandardCharsets.UTF_8.name())
        );
    }

    private static void reportUnreadable(Message dead) {
        String body = new String(dead.getBody(), StandardCharsets.UTF_8);
        String description = "deadlineAt을 읽을 수 없는 포토미션 생성 DLQ 메시지를 버림 messageId=%s, correlationId=%s, body=%s"
                .formatted(dead.getMessageProperties().getMessageId(), dead.getMessageProperties().getCorrelationId(),
                        body.length() <= MAX_LOGGED_BODY_LENGTH ? body : body.substring(0, MAX_LOGGED_BODY_LENGTH));
        log.error(description);
        Sentry.captureMessage(description, SentryLevel.ERROR);
    }

    private static final class Counts {
        private int republished;
        private int expired;
        private int unreadable;
        private boolean stopped;
    }
}
