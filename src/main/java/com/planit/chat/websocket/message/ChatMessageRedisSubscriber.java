package com.planit.chat.websocket.message;

import com.planit.chat.dto.ChatMessageCreatedEvent;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            ChatMessageCreatedEvent event = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8),
                    ChatMessageCreatedEvent.class
            );
            long roomId = Long.parseLong(event.roomId());
            messagingTemplate.convertAndSend(
                    ChatMessageDestinations.roomMessages(roomId),
                    event
            );
        } catch (RuntimeException exception) {
            log.warn("Redis 채팅 메시지 처리에 실패했습니다.", exception);
            Sentry.captureException(exception);
        }
    }
}
