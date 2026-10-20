package com.planit.chat.websocket.message;

import com.planit.chat.dto.ChatMessageCreatedEvent;
import com.planit.chat.dto.ChatMessageHistoryResponse.ChatMessageItemResponse;
import com.planit.chat.dto.ChatMessageHistoryResponse.ChatSenderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageRedisSubscriberTest {

    @DisplayName("Redis 채팅 이벤트를 해당 채팅방의 로컬 WebSocket 구독자에게 전달한다")
    @Test
    void forwardsRedisEventToRoomSubscribers() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ChatMessageRedisSubscriber subscriber = new ChatMessageRedisSubscriber(
                objectMapper,
                messagingTemplate
        );
        ChatMessageCreatedEvent event = event();
        Message message = redisMessage(objectMapper.writeValueAsString(event));

        subscriber.onMessage(message, null);

        verify(messagingTemplate).convertAndSend(
                "/topic/regional-chat-rooms/3001/messages",
                event
        );
    }

    @DisplayName("해석할 수 없는 Redis 메시지는 WebSocket으로 전달하지 않는다")
    @Test
    void doesNotForwardMalformedRedisMessage() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        ChatMessageRedisSubscriber subscriber = new ChatMessageRedisSubscriber(
                objectMapper,
                messagingTemplate
        );
        Message message = redisMessage("{not-json");

        assertThatCode(() -> subscriber.onMessage(message, null))
                .doesNotThrowAnyException();
        verify(messagingTemplate, never()).convertAndSend(
                any(String.class),
                any(ChatMessageCreatedEvent.class)
        );
    }

    private Message redisMessage(String payload) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));
        return message;
    }

    private ChatMessageCreatedEvent event() {
        ChatMessageItemResponse message = new ChatMessageItemResponse(
                "10001",
                "019b1234-5678-7000-8000-123456789abc",
                "TEXT",
                "메시지",
                null,
                new ChatSenderResponse(
                        "01991f6e-7300-7b21-a3cc-1436db3df95e",
                        "플랜잇사용자",
                        null
                ),
                Instant.parse("2026-10-19T07:30:00Z")
        );
        return ChatMessageCreatedEvent.from("3001", message);
    }
}
