package com.planit.mq.dlq;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PhotoMissionGenerationDlqMessagesTest {

    private static final String BODY = """
            {"schemaVersion":1,"jobId":"7","jobType":"PHOTO_MISSION_GENERATE",
             "deadlineAt":"2026-12-01T06:00:00+09:00","request":{}}
            """;

    private final PhotoMissionGenerationDlqMessages messages = new PhotoMissionGenerationDlqMessages(JsonMapper.builder().build());

    @DisplayName("본문의 deadlineAt을 시간대와 함께 읽는다")
    @Test
    void readsDeadline() {
        assertThat(messages.deadlineOf(message(BODY, new MessageProperties())))
                .contains(OffsetDateTime.parse("2026-12-01T06:00:00+09:00"));
    }

    @DisplayName("본문이 JSON이 아니거나 deadlineAt이 없거나 형식이 틀리면 읽지 못한 것으로 본다")
    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "{\"jobId\":\"7\"}",
            "{\"deadlineAt\":123}",
            "{\"deadlineAt\":\"2026-12-01 06:00\"}"
    })
    void rejectsUnreadableDeadline(String body) {
        assertThat(messages.deadlineOf(message(body, new MessageProperties()))).isEmpty();
    }

    @DisplayName("원본 본문·속성·헤더를 옮기고 x-retry-count만 4로 바꾸며, 실패 원인과 dead-letter 기록은 옮기지 않는다")
    @Test
    void buildsReprocessMessage() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setReceivedDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId("request-message-1");
        properties.setCorrelationId("7");
        properties.setType("PHOTO_MISSION_GENERATE");
        properties.setAppId("planit-backend");
        properties.setHeader("x-retry-count", 3);
        properties.setHeader("x-error-kind", "AI_UNAVAILABLE");
        properties.setHeader("x-error-message", "503");
        properties.setHeader("x-failed-at", "2026-12-01T03:00:00+09:00");
        properties.setHeader("x-death", List.of(Map.of("reason", "rejected")));
        properties.setHeader("x-first-death-reason", "rejected");
        properties.setHeader("x-last-death-queue", "ai.photomission-generation.task");
        properties.setHeader("trace-id", "abc");

        Message reprocess = PhotoMissionGenerationDlqMessages.toReprocessMessage(message(BODY, properties));

        MessageProperties copied = reprocess.getMessageProperties();
        assertThat(new String(reprocess.getBody(), StandardCharsets.UTF_8)).isEqualTo(BODY);
        assertThat(copied.getMessageId()).isEqualTo("request-message-1");
        assertThat(copied.getCorrelationId()).isEqualTo("7");
        assertThat(copied.getType()).isEqualTo("PHOTO_MISSION_GENERATE");
        assertThat(copied.getAppId()).isEqualTo("planit-backend");
        assertThat(copied.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(copied.getHeaders())
                .containsEntry("x-retry-count", 4)
                .containsEntry("trace-id", "abc")
                .doesNotContainKeys("x-error-kind", "x-error-message", "x-failed-at", "x-death",
                        "x-first-death-reason", "x-last-death-queue");
    }

    private static Message message(String body, MessageProperties properties) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
