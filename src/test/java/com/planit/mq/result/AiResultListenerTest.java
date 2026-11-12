package com.planit.mq.result;

import com.planit.mq.job.AiJobType;
import com.planit.mq.outbox.OutboxPublisher;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiResultListenerTest {

    private static final long DELIVERY_TAG = 7L;
    private static final String SCHEDULE_RESULT = """
            {"schemaVersion":1,"jobId":"42","jobType":"SCHEDULE_GENERATE","requestMessageId":"m-1",
             "status":"SUCCEEDED","errorKind":null,"result":{"status_code":200}}
            """;

    private final RecordingHandler scheduleHandler = new RecordingHandler(AiJobType.SCHEDULE_GENERATE);
    private final AiResultRepublisher republisher = mock(AiResultRepublisher.class);
    private final Channel channel = mock(Channel.class);
    private final AiResultListener listener = new AiResultListener(
            new AiResultEnvelopeParser(JsonMapper.builder().build()),
            new AiResultHandlers(List.of(scheduleHandler)),
            republisher
    );

    @DisplayName("Handler가 결과를 반영하면 AI 재시도 횟수를 넘기고 ACK한다")
    @Test
    void acksAfterHandled() throws Exception {
        listener.onMessage(message(SCHEDULE_RESULT, Map.of(OutboxPublisher.RETRY_COUNT_HEADER, 2)), channel);

        assertThat(scheduleHandler.handled).hasSize(1);
        assertThat(scheduleHandler.handled.getFirst().jobId()).isEqualTo(42L);
        assertThat(scheduleHandler.retryCounts).containsExactly(2);
        verify(channel).basicAck(DELIVERY_TAG, false);
        verifyNoInteractions(republisher);
    }

    @DisplayName("읽을 수 없는 메시지는 Handler에 넘기지 않고 INVALID_MESSAGE로 응답 DLQ에 보낸 뒤 ACK한다")
    @Test
    void deadLettersInvalidMessage() throws Exception {
        Message message = message("{\"schemaVersion\":2}", Map.of());
        when(republisher.deadLetter(eq(message), eq(AiResultErrorKind.INVALID_MESSAGE), anyString())).thenReturn(true);

        listener.onMessage(message, channel);

        assertThat(scheduleHandler.handled).isEmpty();
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @DisplayName("Handler가 없는 작업 종류는 UNSUPPORTED_JOB_TYPE으로 응답 DLQ에 보낸다")
    @Test
    void deadLettersUnsupportedJobType() throws Exception {
        Message message = message(SCHEDULE_RESULT.replace("SCHEDULE_GENERATE", "PHOTO_JUDGE"), Map.of());
        when(republisher.deadLetter(eq(message), eq(AiResultErrorKind.UNSUPPORTED_JOB_TYPE), anyString()))
                .thenReturn(true);

        listener.onMessage(message, channel);

        verify(republisher).deadLetter(eq(message), eq(AiResultErrorKind.UNSUPPORTED_JOB_TYPE), anyString());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @DisplayName("Handler에서 예외가 나면 Result Retry Queue로 다시 보내고 confirm을 받으면 ACK한다")
    @Test
    void retriesWhenHandlerFails() throws Exception {
        scheduleHandler.failWith = new IllegalStateException("db down");
        Message message = message(SCHEDULE_RESULT, Map.of());
        when(republisher.retryOrDeadLetter(message, scheduleHandler.failWith)).thenReturn(true);

        listener.onMessage(message, channel);

        verify(republisher).retryOrDeadLetter(message, scheduleHandler.failWith);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @DisplayName("재발행 confirm을 받지 못하면 ACK하지 않고 원본을 큐로 되돌린다")
    @Test
    void requeuesWhenRepublishFails() throws Exception {
        scheduleHandler.failWith = new IllegalStateException("db down");
        when(republisher.retryOrDeadLetter(any(), any())).thenReturn(false);

        listener.onMessage(message(SCHEDULE_RESULT, Map.of()), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
    }

    private static Message message(String body, Map<String, Object> headers) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        properties.setMessageId("result-message-1");
        headers.forEach(properties::setHeader);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    private static final class RecordingHandler implements AiResultHandler {

        private final AiJobType jobType;
        private final List<AiResultEnvelope> handled = new ArrayList<>();
        private final List<Integer> retryCounts = new ArrayList<>();
        private RuntimeException failWith;

        private RecordingHandler(AiJobType jobType) {
            this.jobType = jobType;
        }

        @Override
        public AiJobType jobType() {
            return jobType;
        }

        @Override
        public void handle(AiResultEnvelope result, int retryCount) {
            if (failWith != null) {
                throw failWith;
            }
            handled.add(result);
            retryCounts.add(retryCount);
        }
    }
}
