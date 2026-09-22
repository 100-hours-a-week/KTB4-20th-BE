package com.planit.chat.dto;

import com.planit.global.error.ErrorCode;
import java.time.Instant;

public record ChatMessageResultEvent(
        String eventType,
        String status,
        String code,
        String message,
        String clientMessageId,
        String roomId,
        Instant occurredAt,
        AcceptedData data
) {

    private static final String EVENT_TYPE = "CHAT_MESSAGE_RESULT";
    private static final String ACCEPTED_STATUS = "ACCEPTED";
    private static final String REJECTED_STATUS = "REJECTED";
    private static final String ACCEPTED_CODE = "CHAT_MESSAGE_ACCEPTED";
    private static final String ACCEPTED_MESSAGE = "메시지가 전송되었습니다.";

    public static ChatMessageResultEvent accepted(
            String clientMessageId,
            String roomId,
            String messageId,
            Instant createdAt
    ) {
        return new ChatMessageResultEvent(
                EVENT_TYPE,
                ACCEPTED_STATUS,
                ACCEPTED_CODE,
                ACCEPTED_MESSAGE,
                clientMessageId,
                roomId,
                createdAt,
                new AcceptedData(messageId, createdAt)
        );
    }

    public static ChatMessageResultEvent rejected(
            String clientMessageId,
            String roomId,
            ErrorCode errorCode,
            Instant occurredAt
    ) {
        return new ChatMessageResultEvent(
                EVENT_TYPE,
                REJECTED_STATUS,
                errorCode.getCode(),
                errorCode.getMessage(),
                clientMessageId,
                roomId,
                occurredAt,
                null
        );
    }

    public record AcceptedData(
            String messageId,
            Instant createdAt
    ) {
    }
}
