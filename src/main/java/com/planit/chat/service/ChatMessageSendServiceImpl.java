package com.planit.chat.service;

import com.planit.chat.dto.ChatMessageSendRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageSendServiceImpl implements ChatMessageSendService {

    private final ChatMessageTransactionService transactionService;

    @Override
    public ChatMessageSendResult sendTextMessage(
            String userPublicId,
            Long roomId,
            ChatMessageSendRequest request
    ) {
        try {
            return transactionService.createOrFind(
                    userPublicId,
                    roomId,
                    request
            );
        } catch (DataIntegrityViolationException exception) {
            return transactionService.findExistingAfterConflict(
                    userPublicId,
                    roomId,
                    request
            );
        }
    }
}
