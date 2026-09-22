package com.planit.chat.dto;

import com.planit.chat.dto.ChatMessageHistoryResponse.ChatMessageItemResponse;
import java.time.Instant;

public record ChatMessageCreatedEvent(
        String eventType,
        String roomId,
        Instant occurredAt,
        ChatMessageItemResponse data
) {

    private static final String EVENT_TYPE = "CHAT_MESSAGE_CREATED";

    public static ChatMessageCreatedEvent from(
            String roomId,
            ChatMessageItemResponse message
    ) {
        return new ChatMessageCreatedEvent(
                EVENT_TYPE,
                roomId,
                message.createdAt(),
                message
        );
    }
}
