package com.planit.chat.websocket.message;

import com.planit.chat.dto.ChatMessageCreatedEvent;
import com.planit.chat.dto.ChatMessageResultEvent;
import com.planit.chat.service.ChatMessageSendResult;
import com.planit.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ChatMessageEventPublisher {

    private static final String ROOM_DESTINATION_PREFIX =
            "/topic/regional-chat-rooms/";
    private static final String ROOM_DESTINATION_SUFFIX = "/messages";
    private static final String USER_EVENT_DESTINATION = "/queue/chat-events";

    private final SimpMessagingTemplate messagingTemplate;

    public void publishAccepted(
            String userPublicId,
            String sessionId,
            Long roomId,
            ChatMessageSendResult result
    ) {
        String roomIdValue = roomId.toString();
        if (result.created()) {
            messagingTemplate.convertAndSend(
                    roomDestination(roomId),
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

    private String roomDestination(Long roomId) {
        return ROOM_DESTINATION_PREFIX + roomId + ROOM_DESTINATION_SUFFIX;
    }   //해당 경로로 메세지가 매핑
}
