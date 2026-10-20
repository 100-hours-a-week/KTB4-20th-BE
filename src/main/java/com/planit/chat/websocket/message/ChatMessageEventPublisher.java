package com.planit.chat.websocket.message;

import com.planit.chat.dto.ChatMessageCreatedEvent;
import com.planit.chat.dto.ChatMessageResultEvent;
import com.planit.chat.service.ChatMessageSendResult;
import com.planit.global.error.ErrorCode;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChatMessageEventPublisher {

    private static final String USER_EVENT_DESTINATION = "/queue/chat-events";

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRedisPublisher redisPublisher;

    public void publishAccepted(
            String userPublicId,
            String sessionId,
            Long roomId,
            ChatMessageSendResult result
    ) {
        String roomIdValue = roomId.toString();
        if (result.created()) {
            publishToRedis(
                    ChatMessageCreatedEvent.from(roomIdValue, result.message())
            );
        }
        sendToSession(
                userPublicId,
                sessionId,
                ChatMessageResultEvent.accepted(
                        result.message().clientMessageId(),
                        roomIdValue,
                        result.message().messageId(),
                        result.message().createdAt()
                )
        );
    }

    public void publishRejected(
            String userPublicId,
            String sessionId,
            Long roomId,
            String clientMessageId,
            ErrorCode errorCode
    ) {
        sendToSession(
                userPublicId,
                sessionId,
                ChatMessageResultEvent.rejected(
                        clientMessageId,
                        roomId.toString(),
                        errorCode,
                        Instant.now()
                )
        );
    }

    private void publishToRedis(ChatMessageCreatedEvent event) {
        try {
            redisPublisher.publish(event);
        } catch (RuntimeException exception) {
            log.error("채팅 메시지 Redis 발행에 실패했습니다.", exception);
            Sentry.captureException(exception);
        }
    }

    private void sendToSession(
            String userPublicId,
            String sessionId,
            ChatMessageResultEvent event
    ) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(
                SimpMessageType.MESSAGE
        );
        accessor.setSessionId(sessionId);
        accessor.setLeaveMutable(true);
        messagingTemplate.convertAndSendToUser(
                userPublicId,
                USER_EVENT_DESTINATION,
                event,
                accessor.getMessageHeaders()
        );
    }
}
