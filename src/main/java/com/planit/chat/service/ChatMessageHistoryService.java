package com.planit.chat.service;

import com.planit.chat.dto.ChatMessageHistoryResponse;

public interface ChatMessageHistoryService {

    ChatMessageHistoryResponse getMessages(
            String userPublicId,
            Long roomId,
            String cursor,
            Long afterMessageId
    );
}
