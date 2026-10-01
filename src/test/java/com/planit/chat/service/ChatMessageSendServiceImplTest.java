package com.planit.chat.service;

import com.planit.chat.dto.ChatMessageSendRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageSendServiceImplTest {

    @DisplayName("동시 저장 충돌이 발생하면 기존 메시지를 다시 조회한다")
    @Test
    void reloadsExistingMessageAfterConcurrentUniqueConflict() {
        ChatMessageTransactionService transactionService = mock(
                ChatMessageTransactionService.class
        );
        ChatMessageSendServiceImpl service = new ChatMessageSendServiceImpl(
                transactionService
        );
        ChatMessageSendRequest request = new ChatMessageSendRequest(
                UUID.fromString("019b1234-5678-7000-8000-123456789abc"),
                "TEXT",
                "메시지",
                null
        );
        ChatMessageSendResult expected = mock(ChatMessageSendResult.class);
        when(transactionService.createOrFind("user", 3001L, request))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(transactionService.findExistingAfterConflict("user", 3001L, request))
                .thenReturn(expected);

        assertThat(service.sendTextMessage("user", 3001L, request))
                .isSameAs(expected);
        verify(transactionService).findExistingAfterConflict(
                "user",
                3001L,
                request
        );
    }
}
