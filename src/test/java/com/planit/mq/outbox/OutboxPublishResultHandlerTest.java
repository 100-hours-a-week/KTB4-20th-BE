package com.planit.mq.outbox;

import com.planit.mq.config.RabbitMqConfig;
import com.planit.mq.job.AiJobPublishFailureListener;
import com.planit.mq.job.AiJobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.annotation.Transactional;

import java.net.ConnectException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class OutboxPublishResultHandlerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 10, 0);

    @TestBean(name = RabbitMqConfig.MQ_CLOCK, methodName = "fixedClock")
    private Clock clock;

    @Autowired
    private OutboxPublishResultHandler handler;

    @Autowired
    private MessageOutboxRepository repository;

    @Autowired
    private RecordingFailureListener failureListener;

    static Clock fixedClock() {
        return Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    }

    @BeforeEach
    void setUp() {
        failureListener.failures.clear();
    }

    @DisplayName("ack를 받고 return이 없으면 발행 완료로 바꾼다")
    @Test
    void marksPublishedOnAck() {
        MessageOutbox outbox = save();

        handler.handleConfirm(outbox.getId(), new CorrelationData(), new CorrelationData.Confirm(true, null), null);

        MessageOutbox found = find(outbox);
        assertThat(found.getStatus()).isEqualTo(MessageOutboxStatus.PUBLISHED);
        assertThat(found.getPublishedAt()).isEqualTo(NOW);
        assertThat(failureListener.failures).isEmpty();
    }

    @DisplayName("ack와 함께 return을 받으면 재시도 없이 실패 처리하고 작업 실패를 알린다")
    @Test
    void failsImmediatelyOnReturn() {
        MessageOutbox outbox = save();
        CorrelationData correlationData = new CorrelationData();
        correlationData.setReturned(new ReturnedMessage(
                new Message(new byte[0], new MessageProperties()), 312, "NO_ROUTE", "ai.request", "unknown"
        ));

        handler.handleConfirm(outbox.getId(), correlationData, new CorrelationData.Confirm(true, null), null);

        MessageOutbox found = find(outbox);
        assertThat(found.getStatus()).isEqualTo(MessageOutboxStatus.FAILED);
        assertThat(found.getLastError()).isEqualTo("RETURNED: 312 NO_ROUTE");
        assertThat(failureListener.failures).containsExactly(
                new Failure(AiJobType.SCHEDULE_GENERATE, 10L, PublishFailureCode.PUBLISH_RETURNED)
        );
    }

    @DisplayName("NACK을 받으면 NACK 횟수를 올리고 5~7.5초 뒤로 재시도를 미룬다")
    @Test
    void schedulesRetryOnFirstNack() {
        MessageOutbox outbox = save();

        handler.handleConfirm(outbox.getId(), new CorrelationData(), new CorrelationData.Confirm(false, "reject-publish"), null);

        MessageOutbox found = find(outbox);
        assertThat(found.getStatus()).isEqualTo(MessageOutboxStatus.PENDING);
        assertThat(found.getNackAttemptCount()).isOne();
        assertThat(found.getNextAttemptAt()).isBetween(NOW.plusSeconds(5), NOW.plusNanos(7_500_000_000L));
        assertThat(found.getLastError()).isEqualTo("NACK: reject-publish");
    }

    @DisplayName("4번째 NACK에서 실패 처리한다")
    @Test
    void failsOnFourthNack() {
        MessageOutbox outbox = save();
        CorrelationData.Confirm nack = new CorrelationData.Confirm(false, "reject-publish");

        for (int i = 0; i < 3; i++) {
            handler.handleConfirm(outbox.getId(), new CorrelationData(), nack, null);
        }
        assertThat(find(outbox).getStatus()).isEqualTo(MessageOutboxStatus.PENDING);

        handler.handleConfirm(outbox.getId(), new CorrelationData(), nack, null);

        assertThat(find(outbox).getStatus()).isEqualTo(MessageOutboxStatus.FAILED);
        assertThat(failureListener.failures).containsExactly(
                new Failure(AiJobType.SCHEDULE_GENERATE, 10L, PublishFailureCode.PUBLISH_FAILED)
        );
    }

    @DisplayName("confirm 시간 초과는 유실 횟수를 올리고, 5번째에서 실패 처리한다")
    @Test
    void failsOnFifthConfirmTimeout() {
        MessageOutbox outbox = save();

        for (int i = 0; i < 4; i++) {
            handler.handleConfirm(outbox.getId(), new CorrelationData(), null, new CompletionException(new TimeoutException()));
        }
        MessageOutbox pending = find(outbox);
        assertThat(pending.getStatus()).isEqualTo(MessageOutboxStatus.PENDING);
        assertThat(pending.getFailureAttemptCount()).isEqualTo(4);
        assertThat(pending.getLastError()).isEqualTo("TIMEOUT: confirm not received");

        handler.handleConfirm(outbox.getId(), new CorrelationData(), null, new TimeoutException());

        assertThat(find(outbox).getStatus()).isEqualTo(MessageOutboxStatus.FAILED);
        assertThat(failureListener.failures).hasSize(1);
    }

    @DisplayName("발행 중 연결 예외는 confirm 유실과 같이 재시도한다")
    @Test
    void schedulesRetryOnSendFailure() {
        MessageOutbox outbox = save();

        handler.handleSendFailure(outbox.getId(), new AmqpConnectException(new ConnectException("refused")));

        MessageOutbox found = find(outbox);
        assertThat(found.getFailureAttemptCount()).isOne();
        assertThat(found.getLastError()).startsWith("SEND_ERROR: AmqpConnectException");
    }

    @DisplayName("이미 끝난 메시지의 결과는 무시하고 작업 실패도 다시 알리지 않는다")
    @Test
    void ignoresResultOfFinishedMessage() {
        MessageOutbox outbox = save();
        handler.handleConfirm(outbox.getId(), new CorrelationData(), new CorrelationData.Confirm(true, null), null);

        handler.handleConfirm(outbox.getId(), new CorrelationData(), new CorrelationData.Confirm(false, "late"), null);
        handler.handleSendFailure(outbox.getId(), new TimeoutException());

        MessageOutbox found = find(outbox);
        assertThat(found.getStatus()).isEqualTo(MessageOutboxStatus.PUBLISHED);
        assertThat(found.getNackAttemptCount()).isZero();
        assertThat(found.getFailureAttemptCount()).isZero();
        assertThat(failureListener.failures).isEmpty();
    }

    @DisplayName("실패 이유는 500자까지만 저장한다")
    @Test
    void truncatesLastError() {
        MessageOutbox outbox = save();

        handler.handleConfirm(outbox.getId(), new CorrelationData(), new CorrelationData.Confirm(false, "x".repeat(600)), null);

        assertThat(find(outbox).getLastError()).hasSize(500);
    }

    private MessageOutbox save() {
        return repository.saveAndFlush(MessageOutbox.pending(
                AiJobType.SCHEDULE_GENERATE, 10L, "ai.request", "ai-schedule-generation", "{}", NOW, NOW.plusSeconds(10)
        ));
    }

    private MessageOutbox find(MessageOutbox outbox) {
        return repository.findById(outbox.getId()).orElseThrow();
    }

    record Failure(AiJobType jobType, Long jobId, PublishFailureCode failureCode) {
    }

    static class RecordingFailureListener implements AiJobPublishFailureListener {

        private final List<Failure> failures = new ArrayList<>();

        @Override
        public void onPublishFailed(AiJobType jobType, Long jobId, PublishFailureCode failureCode) {
            failures.add(new Failure(jobType, jobId, failureCode));
        }
    }

    @TestConfiguration
    static class FailureListenerConfig {

        @Bean
        RecordingFailureListener recordingFailureListener() {
            return new RecordingFailureListener();
        }
    }
}
