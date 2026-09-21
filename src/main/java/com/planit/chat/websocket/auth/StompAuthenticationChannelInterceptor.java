package com.planit.chat.websocket.auth;

import com.planit.chat.error.ChatAuthenticationException;
import com.planit.chat.error.ChatErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompAuthenticationChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ObjectProvider<ChatAccessTokenAuthenticator> authenticatorProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            authenticateConnect(accessor);
        } else if (requiresAuthentication(command) && accessor.getUser() == null) {
            throw new ChatAuthenticationException(
                    ChatErrorCode.STOMP_AUTHENTICATION_REQUIRED
            );
        }

        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new ChatAuthenticationException(ChatErrorCode.ACCESS_TOKEN_REQUIRED);
        }

        String accessToken = authorization.substring(BEARER_PREFIX.length()).trim();
        if (accessToken.isEmpty()) {
            throw new ChatAuthenticationException(ChatErrorCode.ACCESS_TOKEN_REQUIRED);
        }

        ChatAccessTokenAuthenticator authenticator = authenticatorProvider.getIfAvailable();
        if (authenticator == null) {
            throw new ChatAuthenticationException(
                    ChatErrorCode.ACCESS_TOKEN_AUTHENTICATOR_UNAVAILABLE
            );
        }

        Authentication authentication = authenticator.authenticate(accessToken);
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ChatAuthenticationException(ChatErrorCode.INVALID_ACCESS_TOKEN);
        }

        accessor.setUser(authentication);
    }

    private boolean requiresAuthentication(StompCommand command) {
        return StompCommand.SUBSCRIBE.equals(command)
                || StompCommand.SEND.equals(command);
    }
}
