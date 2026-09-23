package com.planit.chat.websocket.auth;

import com.planit.chat.error.ChatAuthenticationException;
import com.planit.chat.error.ChatErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class StompAuthenticationChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final ChatAccessTokenAuthenticator authenticator;

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
        } else if (requiresAuthentication(command)) {
            validateExistingAuthentication(accessor.getUser());
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

        Authentication authentication = authenticator.authenticate(accessToken);
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ChatAuthenticationException(ChatErrorCode.INVALID_ACCESS_TOKEN);
        }

        accessor.setUser(authentication);
    }

    private void validateExistingAuthentication(Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !authentication.isAuthenticated()) {
            throw new ChatAuthenticationException(
                    ChatErrorCode.STOMP_AUTHENTICATION_REQUIRED
            );
        }
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            Instant expiresAt = jwtAuthentication.getToken().getExpiresAt();
            if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
                throw new ChatAuthenticationException(
                        ChatErrorCode.INVALID_ACCESS_TOKEN
                );
            }
        }
    }

    private boolean requiresAuthentication(StompCommand command) {
        return StompCommand.SUBSCRIBE.equals(command)
                || StompCommand.SEND.equals(command);
    }
}
