package com.planit.chat.websocket.subscription;

import com.planit.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class ChatStompErrorHandler extends StompSubProtocolErrorHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Message<byte[]> handleClientMessageProcessingError(
            Message<byte[]> clientMessage,
            Throwable exception
    ) {
        ChatSubscriptionException subscriptionException = findSubscriptionException(exception);
        if (subscriptionException == null) {
            return super.handleClientMessageProcessingError(clientMessage, exception);
        }

        StompHeaderAccessor errorAccessor = StompHeaderAccessor.create(StompCommand.ERROR);
        errorAccessor.setMessage(subscriptionException.getErrorCode().getCode());
        errorAccessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        errorAccessor.setLeaveMutable(true);

        StompHeaderAccessor clientAccessor = clientMessage == null
                ? null
                : MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class);
        if (clientAccessor != null && clientAccessor.getReceipt() != null) {
            errorAccessor.setReceiptId(clientAccessor.getReceipt());
        }

        byte[] payload = objectMapper.writeValueAsBytes(ApiResponse.error(
                subscriptionException.getErrorCode().getCode(),
                subscriptionException.getErrorCode().getMessage()
        ));
        return MessageBuilder.createMessage(payload, errorAccessor.getMessageHeaders());
    }

    private ChatSubscriptionException findSubscriptionException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ChatSubscriptionException subscriptionException) {
                return subscriptionException;
            }
            current = current.getCause();
        }
        return null;
    }
}
