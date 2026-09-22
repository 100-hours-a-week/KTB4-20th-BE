package com.planit.chat.service;

import com.planit.chat.dto.ChatMessageHistoryResponse.ChatMessageItemResponse;

public record ChatMessageSendResult(
        ChatMessageItemResponse message,
        boolean created
) {
}
