package com.planit.chat.websocket.message;

import com.planit.chat.dto.ChatMessageSendRequest;
import com.planit.chat.service.ChatMessageSendResult;
import com.planit.chat.service.ChatMessageSendService;
import com.planit.global.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class RegionalChatMessageController {

    private static final String SESSION_ID_HEADER = "simpSessionId";

    private final ChatMessageSendService messageSendService;
    private final ChatMessageEventPublisher eventPublisher;

    @MessageMapping("/regional-chat-rooms/{roomId}/messages")
    public void sendMessage(
            Principal principal,
            @Header(SESSION_ID_HEADER) String sessionId,
            @DestinationVariable Long roomId,
            ChatMessageSendRequest request
    ) {
        try {
            ChatMessageSendResult result = messageSendService.sendTextMessage(
                    principal.getName(),
                    roomId,
                    request
            );
            eventPublisher.publishAccepted(
                    principal.getName(),
                    sessionId,
                    roomId,
                    result
            );
        } catch (BusinessException exception) {
            eventPublisher.publishRejected(
                    principal.getName(),
                    sessionId,
                    roomId,
                    request == null || request.clientMessageId() == null
                            ? null
                            : request.clientMessageId().toString(),
                    exception.getErrorCode()
            );
        }
    }
}
