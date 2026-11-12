package com.planit.mq.dlq;

import com.planit.mq.config.RabbitMqProperties;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 실제 브로커로 만들기 어려운 confirm 실패를 확인한다. 정상 재처리는 통합 테스트에서 확인한다. */
class PhotoMissionGenerationDlqReprocessorTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZonedDateTime FIVE_AM = ZonedDateTime.of(2026, 12, 1, 5, 0, 0, 0, SEOUL);
    private static final byte[] BODY = "{\"deadlineAt\":\"2026-12-01T06:00:00+09:00\"}".getBytes(StandardCharsets.UTF_8);

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final Channel channel = mock(Channel.class);
    private final PhotoMissionGenerationDlqReprocessor reprocessor = new PhotoMissionGenerationDlqReprocessor(
            rabbitTemplate,
            new PhotoMissionGenerationDlqMessages(JsonMapper.builder().build()),
            new RabbitMqProperties(Duration.ofMillis(100)),
            Clock.fixed(FIVE_AM.toInstant(), SEOUL)
    );

    @DisplayName("재발행 confirm으로 NACK을 받으면 그 메시지를 DLQ에 되돌리고 남은 메시지를 꺼내지 않는다")
    @Test
    void stopsOnNack() throws Exception {
        givenDlqMessages();
        confirmWith(correlationData -> correlationData.getFuture().complete(new CorrelationData.Confirm(false, "nack")));

        reprocessor.reprocess();

        verify(channel).basicNack(1L, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, times(1)).basicGet(anyString(), anyBoolean());
    }

    @DisplayName("confirm 대기 시간 안에 confirm이 오지 않아도 DLQ에 되돌리고 멈춘다")
    @Test
    void stopsOnConfirmTimeout() throws Exception {
        givenDlqMessages();
        confirmWith(correlationData -> { });

        reprocessor.reprocess();

        verify(channel).basicNack(1L, false, true);
        verify(channel, times(1)).basicGet(anyString(), anyBoolean());
    }

    @DisplayName("05:00(서울)에 실행하고 생성 작업임이 드러나는 이름의 60초 락을 잡는다")
    @Test
    void runsAtFiveWithLock() throws NoSuchMethodException {
        var reprocess = PhotoMissionGenerationDlqReprocessor.class.getMethod("reprocess");

        assertThat(reprocess.getAnnotation(Scheduled.class).cron()).isEqualTo("0 0 5 * * *");
        assertThat(reprocess.getAnnotation(Scheduled.class).zone()).isEqualTo("Asia/Seoul");
        assertThat(reprocess.getAnnotation(SchedulerLock.class).name())
                .isEqualTo("photoMissionGenerationDlqReprocessor");
        assertThat(reprocess.getAnnotation(SchedulerLock.class).lockAtMostFor()).isEqualTo("PT60S");
    }

    @SuppressWarnings("unchecked")
    private void givenDlqMessages() throws Exception {
        when(rabbitTemplate.execute(any(ChannelCallback.class)))
                .thenAnswer(invocation -> ((ChannelCallback<Object>) invocation.getArgument(0)).doInRabbit(channel));
        when(channel.basicGet(anyString(), anyBoolean()))
                .thenReturn(response(1L), response(2L), null);
    }

    private void confirmWith(Consumer<CorrelationData> confirm) {
        doAnswer(invocation -> {
            confirm.accept(invocation.getArgument(3));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private static GetResponse response(long deliveryTag) {
        Envelope envelope = new Envelope(deliveryTag, false, "ai.dlx", "ai-photomission-generation.dlq");
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .messageId("request-" + deliveryTag)
                .correlationId(String.valueOf(deliveryTag))
                .type("PHOTO_MISSION_GENERATE")
                .build();
        return new GetResponse(envelope, properties, BODY, 1);
    }
}
