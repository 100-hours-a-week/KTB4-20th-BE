package com.planit.photomission.messaging;

import com.planit.mq.dlq.AiDlqListener;
import com.planit.mq.outbox.OutboxPublisher;
import com.planit.photomission.service.PhotoEvaluationFailureService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PhotoEvaluationDlqListenerTest {

    private static final long DELIVERY_TAG = 9L;

    private final PhotoEvaluationFailureService failureService = mock(PhotoEvaluationFailureService.class);
    private final PhotoEvaluationDlqListener listener = new PhotoEvaluationDlqListener(failureService);
    private final Channel channel = mock(Channel.class);

    @DisplayName("PHOTO_JUDGE 요청 DLQ는 x-retry-count 3을 네 번째 실행 실패로 반영하고 커밋 후 ACK한다")
    @Test
    void failsEvaluationThenAcks() throws Exception {
        Message message = message(Map.of(
                AiDlqListener.ERROR_KIND_HEADER, "AI_TIMEOUT",
                OutboxPublisher.RETRY_COUNT_HEADER, 3
        ));

        listener.onPhotoEvaluationDlq(message, channel);

        verify(failureService).failFromDlq(51L, 4, "AI_TIMEOUT");
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @DisplayName("DLQ 실패 처리 중 예외가 나면 ACK하지 않고 재전달한다")
    @Test
    void requeuesOnFailure() throws Exception {
        when(failureService.failFromDlq(51L, 1, "DEAD_LETTERED"))
                .thenThrow(new IllegalStateException("db down"));

        listener.onPhotoEvaluationDlq(message(Map.of()), channel);

        verify(channel).basicNack(DELIVERY_TAG, false, true);
        verify(channel, never()).basicAck(DELIVERY_TAG, false);
    }

    @DisplayName("x-retry-count가 0부터 3이 아니면 작업을 변경하지 않고 ACK한다")
    @Test
    void dropsInvalidRetryCount() throws Exception {
        listener.onPhotoEvaluationDlq(
                message(Map.of(OutboxPublisher.RETRY_COUNT_HEADER, 4)),
                channel
        );

        verifyNoInteractions(failureService);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    private static Message message(Map<String, Object> headers) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(DELIVERY_TAG);
        properties.setMessageId("photo-51");
        properties.setCorrelationId("51");
        properties.setType("PHOTO_JUDGE");
        headers.forEach(properties::setHeader);
        return new Message("{}".getBytes(), properties);
    }
}
