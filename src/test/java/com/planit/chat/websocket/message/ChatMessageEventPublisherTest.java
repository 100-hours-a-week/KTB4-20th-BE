package com.planit.chat.websocket.message;

import com.planit.chat.dto.ChatMessageCreatedEvent;
import com.planit.chat.dto.ChatMessageHistoryResponse.ChatMessageItemResponse;
import com.planit.chat.dto.ChatMessageHistoryResponse.ChatSenderResponse;
import com.planit.chat.dto.ChatMessageResultEvent;
import com.planit.chat.service.ChatMessageSendResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ChatMessageEventPublisherTest {

    private static final String USER_PUBLIC_ID =
            "01991f6e-7300-7b21-a3cc-1436db3df95e";

    @Test
    void publishesNewMessageToRoomAndResultOnlyToRequestSession() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ChatMessageEventPublisher publisher = new ChatMessageEventPublisher(
                messagingTemplate
        );
        ChatMessageSendResult result = new ChatMessageSendResult(message(), true);

        publisher.publishAccepted(USER_PUBLIC_ID, "session-1", 3001L, result);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/regional-chat-rooms/3001/messages"),
                any(ChatMessageCreatedEvent.class)
        );
        ArgumentCaptor<ChatMessageResultEvent> eventCaptor = ArgumentCaptor.forClass(
                ChatMessageResultEvent.class
        );
        ArgumentCaptor<MessageHeaders> headerCaptor = ArgumentCaptor.forClass(
                MessageHeaders.class
        );
        verify(messagingTemplate).convertAndSendToUser(
                eq(USER_PUBLIC_ID),
                eq("/queue/chat-events"),
                eventCaptor.capture(),
                headerCaptor.capture()
        );
        assertThat(eventCaptor.getValue().status()).isEqualTo("ACCEPTED");
        assertThat(headerCaptor.getValue().get(
                SimpMessageHeaderAccessor.SESSION_ID_HEADER
        )).isEqualTo("session-1");
    }

    @Test
    void doesNotBroadcastExistingMessageAgain() {
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ChatMessageEventPublisher publisher = new ChatMessageEventPublisher(
                messagingTemplate
        );
        ChatMessageSendResult result = new ChatMessageSendResult(message(), false);

        publisher.publishAccepted(USER_PUBLIC_ID, "session-1", 3001L, result);

        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/regional-chat-rooms/3001/messages"),
                any(ChatMessageCreatedEvent.class)
        );
        verify(messagingTemplate).convertAndSendToUser(
                eq(USER_PUBLIC_ID),
                eq("/queue/chat-events"),
                any(ChatMessageResultEvent.class),
                any(MessageHeaders.class)
        );
    }

    private ChatMessageItemResponse message() {
        return new ChatMessageItemResponse(
                "10001",
                "019b1234-5678-7000-8000-123456789abc",
                "TEXT",
                "메시지",
                null,
                new ChatSenderResponse(
                        USER_PUBLIC_ID,
                        "플랜잇사용자",
                        "https://example.com/profile.svg"
                ),
                Instant.parse("2026-09-22T12:00:00Z")
        );
    }
}
