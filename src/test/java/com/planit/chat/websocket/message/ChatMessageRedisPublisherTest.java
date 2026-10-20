package com.planit.chat.websocket.message;

import com.planit.chat.dto.ChatMessageCreatedEvent;
import com.planit.chat.dto.ChatMessageHistoryResponse.ChatMessageItemResponse;
import com.planit.chat.dto.ChatMessageHistoryResponse.ChatSenderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatMessageRedisPublisherTest {

    @DisplayName("생성된 채팅 메시지를 JSON으로 직렬화해 채팅 전용 Redis 채널로 발행한다")
    @Test
    void publishesCreatedMessageToChatChannel() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ObjectMapper objectMapper = JsonMapper.builder().build();
        ChatMessageRedisPublisher publisher = new ChatMessageRedisPublisher(
                redisTemplate,
                objectMapper,
                ChannelTopic.of("planit:chat:messages")
        );
        ChatMessageCreatedEvent event = event();

        publisher.publish(event);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(
                eq("planit:chat:messages"),
                payload.capture()
        );
        JsonNode json = objectMapper.readTree(payload.getValue());
        assertThat(json.get("eventType").stringValue())
                .isEqualTo("CHAT_MESSAGE_CREATED");
        assertThat(json.get("roomId").stringValue()).isEqualTo("3001");
        assertThat(json.get("data").get("messageId").stringValue())
                .isEqualTo("10001");
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
