package com.planit.chat.websocket.subscription;

import com.planit.chat.error.ChatAuthenticationException;
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
        ChatAuthenticationException authenticationException =
                findException(exception, ChatAuthenticationException.class);
        if (authenticationException != null) {
            return errorMessage(
                    clientMessage,
                    authenticationException.getErrorCode().getCode(),
                    authenticationException.getErrorCode().getMessage()
            );
        }
        ChatSubscriptionException subscriptionException = findSubscriptionException(exception);
        if (subscriptionException == null) {
            return super.handleClientMessageProcessingError(clientMessage, exception);
        }

        return errorMessage(
                clientMessage,
                subscriptionException.getErrorCode().getCode(),
                subscriptionException.getErrorCode().getMessage()
        );
    }

    private Message<byte[]> errorMessage(
            Message<byte[]> clientMessage,
            String errorCode,
            String errorMessage
    ) {

        StompHeaderAccessor errorAccessor = StompHeaderAccessor.create(StompCommand.ERROR);
        errorAccessor.setMessage(errorCode);
        errorAccessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        errorAccessor.setLeaveMutable(true);

        StompHeaderAccessor clientAccessor = clientMessage == null
                ? null
                : MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class);
        if (clientAccessor != null && clientAccessor.getReceipt() != null) {
            errorAccessor.setReceiptId(clientAccessor.getReceipt());
        }

        byte[] payload = objectMapper.writeValueAsBytes(ApiResponse.error(
                errorCode,
                errorMessage
        ));
        return MessageBuilder.createMessage(payload, errorAccessor.getMessageHeaders());
    }

    private ChatSubscriptionException findSubscriptionException(Throwable exception) {
        return findException(exception, ChatSubscriptionException.class);
    }

    private <T extends Throwable> T findException(
            Throwable exception,
            Class<T> exceptionType
    ) {
        Throwable current = exception;
        while (current != null) {
            if (exceptionType.isInstance(current)) {
                return exceptionType.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
