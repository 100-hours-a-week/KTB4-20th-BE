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

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ChatStompErrorHandler extends StompSubProtocolErrorHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Message<byte[]> handleClientMessageProcessingError(
            Message<byte[]> clientMessage,
            Throwable exception
    ) {
        return resolveError(exception)
                .map(error -> createErrorFrame(clientMessage, error))
                .orElseGet(() -> handleUnknownError(clientMessage, exception));
    }

    private Optional<ChatStompError> resolveError(Throwable exception) {
        return resolveAuthenticationError(exception)
                .or(() -> resolveSubscriptionError(exception));
    }

    private Optional<ChatStompError> resolveAuthenticationError(
            Throwable exception
    ) {
        return Optional.ofNullable(findException(
                exception,
                ChatAuthenticationException.class
        )).map(authenticationException -> new ChatStompError(
                authenticationException.getErrorCode().getCode(),
                authenticationException.getErrorCode().getMessage()
        ));
    }

    private Optional<ChatStompError> resolveSubscriptionError(
            Throwable exception
    ) {
        return Optional.ofNullable(findException(
                exception,
                ChatSubscriptionException.class
        )).map(subscriptionException -> new ChatStompError(
                subscriptionException.getErrorCode().getCode(),
                subscriptionException.getErrorCode().getMessage()
        ));
    }

    private Message<byte[]> createErrorFrame(
            Message<byte[]> clientMessage,
            ChatStompError error
    ) {
        StompHeaderAccessor errorAccessor = StompHeaderAccessor.create(StompCommand.ERROR);
        errorAccessor.setMessage(error.code());
        errorAccessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        errorAccessor.setLeaveMutable(true);

        StompHeaderAccessor clientAccessor = clientMessage == null
                ? null
                : MessageHeaderAccessor.getAccessor(clientMessage, StompHeaderAccessor.class);
        if (clientAccessor != null && clientAccessor.getReceipt() != null) {
            errorAccessor.setReceiptId(clientAccessor.getReceipt());
        }

        byte[] payload = objectMapper.writeValueAsBytes(ApiResponse.error(
                error.code(),
                error.message()
        ));
        return MessageBuilder.createMessage(payload, errorAccessor.getMessageHeaders());
    }

    private Message<byte[]> handleUnknownError(
            Message<byte[]> clientMessage,
            Throwable exception
    ) {
        return super.handleClientMessageProcessingError(clientMessage, exception);
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

    private record ChatStompError(
            String code,
            String message
    ) {
    }
}
