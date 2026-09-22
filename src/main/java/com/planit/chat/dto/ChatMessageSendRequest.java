package com.planit.chat.dto;

import java.util.UUID;

public record ChatMessageSendRequest(
        UUID clientMessageId,
        String messageType,
        String text,
        String uploadKey
) {
}
