package com.planit.mq.dlq;

import com.planit.mq.job.AiJobTerminationService;
import com.planit.mq.job.AiJobType;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiDlqListenerTest {

    private static final long DELIVERY_TAG = 9L;

    private final AiJobTerminationService terminationService = mock(AiJobTerminationService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final Channel channel = mock(Channel.class);
    private final AiDlqListener listener = new AiDlqListener(terminationService, transactionManager);

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
    }

    @DisplayName("correlation_id와 type으로 작업을 찾아 x-error-kind로 실패 처리하고 커밋한 뒤 ACK한다")
    @Test
    void failsJobThenAcks() throws Exception {
        Message message = message("42", "SCHEDULE_GENERATE", Map.of(AiDlqListener.ERROR_KIND_HEADER, "AI_TIMEOUT"));

        listener.onScheduleGenerationDlq(message, channel);

        verify(terminationService).fail(AiJobType.SCHEDULE_GENERATE, 42L, "AI_TIMEOUT");
        verify(transactionManager).commit(any());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @DisplayName("실패 처리 중 예외가 나면 ACK하지 않고 큐로 되돌린다")
    @Test
    void requeuesOnError() throws Exception {
        when(terminationService.fail(any(), any(), any())).thenThrow(new IllegalStateException("db down"));

        listener.onScheduleGenerationDlq(message("42", "SCHEDULE_GENERATE", Map.of()), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
    }

    @DisplayName("작업 ID를 읽을 수 없거나 큐와 다른 작업 종류면 실패 처리하지 않고 ACK해 버린다")
    @Test
    void dropsUnidentifiableMessage() throws Exception {
        listener.onScheduleGenerationDlq(message(null, "SCHEDULE_GENERATE", Map.of()), channel);
        listener.onScheduleGenerationDlq(message("abc", "SCHEDULE_GENERATE", Map.of()), channel);
        listener.onScheduleGenerationDlq(message("42", "PHOTO_JUDGE", Map.of()), channel);
        listener.onScheduleGenerationDlq(message("42", null, Map.of()), channel);

        verifyNoInteractions(terminationService);
        verify(channel, times(4)).basicAck(DELIVERY_TAG, false);
    }

    @DisplayName("x-error-kind가 없으면 마지막 dead-letter 사유가 delivery_limit일 때 DELIVERY_LIMIT, 그 밖에는 DEAD_LETTERED다")
    @Test
    void decidesErrorCodeWithoutErrorKind() {
        MessageProperties deliveryLimit = new MessageProperties();
        deliveryLimit.setHeader("x-death", List.of(Map.of("reason", "delivery_limit", "count", 1L)));
        MessageProperties rejected = new MessageProperties();
        rejected.setHeader("x-death", List.of(Map.of("reason", "rejected", "count", 1L)));

        assertThat(AiDlqListener.errorCodeOf(deliveryLimit)).isEqualTo("DELIVERY_LIMIT");
        assertThat(AiDlqListener.errorCodeOf(rejected)).isEqualTo("DEAD_LETTERED");
        assertThat(AiDlqListener.errorCodeOf(new MessageProperties())).isEqualTo("DEAD_LETTERED");
    }

    @DisplayName("x-error-kind는 작업 테이블 error_code 길이(100자)까지만 남긴다")
    @Test
    void truncatesLongErrorKind() {
        MessageProperties properties = new MessageProperties();
        properties.setHeader(AiDlqListener.ERROR_KIND_HEADER, "E".repeat(150));

        assertThat(AiDlqListener.errorCodeOf(properties)).hasSize(100);
    }

    private static Message message(String correlationId, String type, Map<String, Object> headers) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        properties.setMessageId("request-message-1");
        properties.setCorrelationId(correlationId);
        properties.setType(type);
        headers.forEach(properties::setHeader);
        return new Message("{}".getBytes(), properties);
    }
}
