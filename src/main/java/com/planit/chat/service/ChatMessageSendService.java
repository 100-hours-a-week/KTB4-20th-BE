package com.planit.chat.service;

import com.planit.chat.dto.ChatMessageSendRequest;

public interface ChatMessageSendService {

    ChatMessageSendResult sendTextMessage(
            String userPublicId,
            Long roomId,
            ChatMessageSendRequest request
    );
}
