package com.planit.mq.dlq;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

/** 05시 재처리에서 포토미션 생성 요청 DLQ 메시지를 읽고 다시 보낼 메시지를 만든다. */
@Component
public class PhotoMissionGenerationDlqMessages {

    public static final String RETRY_COUNT_HEADER = "x-retry-count";
    /** 05시 재처리 메시지의 x-retry-count. AI Worker는 이 값으로 마지막 시도임을 안다(계약 4.2절). */
    public static final int REPROCESS_RETRY_COUNT = 4;

    private final ObjectMapper objectMapper;

    public PhotoMissionGenerationDlqMessages(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** @return 본문이 JSON이 아니거나 deadlineAt이 없거나 ISO-8601이 아니면 비어 있다 */
    public Optional<OffsetDateTime> deadlineOf(Message message) {
        try {
            JsonNode deadlineAt = objectMapper.readTree(message.getBody()).get("deadlineAt");
            if (deadlineAt == null || !deadlineAt.isString()) {
                return Optional.empty();
            }
            return Optional.of(OffsetDateTime.parse(deadlineAt.asString()));
        } catch (JacksonException | DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * 원본 본문, 메시지 ID, 작업 ID, 종류와 헤더를 그대로 옮기고 x-retry-count만 4로 바꾼다.
     * DLQ로 보낼 때 붙인 x-error 계열 헤더와 RabbitMQ가 붙인 x-death 계열 헤더는 옮기지 않는다.
     */
    public static Message toReprocessMessage(Message dead) {
        MessageProperties source = dead.getMessageProperties();
        MessageProperties copy = new MessageProperties();
        copy.setContentType(source.getContentType());
        copy.setContentEncoding(source.getContentEncoding());
        if (source.getReceivedDeliveryMode() != null) {
            copy.setDeliveryMode(source.getReceivedDeliveryMode());
        }
        copy.setMessageId(source.getMessageId());
        copy.setCorrelationId(source.getCorrelationId());
        copy.setType(source.getType());
        copy.setTimestamp(source.getTimestamp());
        copy.setAppId(source.getAppId());
        for (Map.Entry<String, Object> header : source.getHeaders().entrySet()) {
            if (!isDroppedHeader(header.getKey())) {
                copy.setHeader(header.getKey(), header.getValue());
            }
        }
        copy.setHeader(RETRY_COUNT_HEADER, REPROCESS_RETRY_COUNT);
        return new Message(dead.getBody(), copy);
    }

    private static boolean isDroppedHeader(String name) {
        return name.startsWith("x-error-")
                || name.equals("x-failed-at")
                || name.equals("x-death")
                || name.startsWith("x-first-death-")
                || name.startsWith("x-last-death-");
    }
}
