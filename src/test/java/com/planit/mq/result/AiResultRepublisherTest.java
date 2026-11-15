package com.planit.mq.result;

import com.planit.mq.config.RabbitMqProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/** 실제 브로커로 만들기 어려운 confirm 실패를 확인한다. 정상 발행은 통합 테스트에서 확인한다. */
class AiResultRepublisherTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final AiResultRepublisher republisher = new AiResultRepublisher(
            rabbitTemplate,
            new RabbitMqProperties(
                    Duration.ofMillis(100),
                    new RabbitMqProperties.Outbox(Duration.ofSeconds(5), Duration.ofSeconds(5), 3, 4)
            ),
            Clock.system(ZoneId.of("Asia/Seoul"))
    );

    @DisplayName("NACK을 받으면 실패로 알려 원본을 되돌리게 한다")
    @Test
    void failsOnNack() {
        confirmWith(correlationData -> correlationData.getFuture().complete(new CorrelationData.Confirm(false, "nack")));

        assertThat(republisher.retryOrDeadLetter(message(), new IllegalStateException())).isFalse();
    }

    @DisplayName("ACK여도 큐로 가지 못하고 돌아왔으면 실패로 본다")
    @Test
    void failsWhenReturned() {
        confirmWith(correlationData -> {
            correlationData.setReturned(mock(ReturnedMessage.class));
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
        });

        assertThat(republisher.deadLetter(message(), AiResultErrorKind.INVALID_MESSAGE, "bad")).isFalse();
    }

    @DisplayName("confirm 대기 시간 안에 confirm이 오지 않거나 발행에서 예외가 나면 실패로 본다")
    @Test
    void failsOnTimeoutOrSendError() {
        confirmWith(correlationData -> { });
        assertThat(republisher.retryOrDeadLetter(message(), new IllegalStateException())).isFalse();

        doThrow(new IllegalStateException("connection closed"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThat(republisher.retryOrDeadLetter(message(), new IllegalStateException())).isFalse();
    }

    @DisplayName("재시도 횟수 헤더가 없거나 읽을 수 없으면 0으로 본다")
    @Test
    void readsMissingRetryCountAsZero() {
        assertThat(AiResultRepublisher.resultRetryCountOf(message())).isZero();

        Message withText = message();
        withText.getMessageProperties().setHeader(AiResultRepublisher.RESULT_RETRY_COUNT_HEADER, "2");
        assertThat(AiResultRepublisher.resultRetryCountOf(withText)).isEqualTo(2);

        Message withInvalid = message();
        withInvalid.getMessageProperties().setHeader(AiResultRepublisher.RESULT_RETRY_COUNT_HEADER, "abc");
        assertThat(AiResultRepublisher.resultRetryCountOf(withInvalid)).isZero();
    }

    private void confirmWith(Consumer<CorrelationData> confirm) {
        doAnswer(invocation -> {
            confirm.accept(invocation.getArgument(3));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private static Message message() {
        return new Message("{}".getBytes(), new MessageProperties());
    }
}
