package com.planit.chat.websocket.auth;

import com.planit.chat.error.ChatAuthenticationException;
import com.planit.chat.error.ChatErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StompAuthenticationChannelInterceptorTest {

    private static final String USER_PUBLIC_ID =
            "01991f6e-7300-7b21-a3cc-1436db3df95e";

    private ChatAccessTokenAuthenticator authenticator;
    private StompAuthenticationChannelInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    void setUp() {
        authenticator = mock(ChatAccessTokenAuthenticator.class);
        interceptor = new StompAuthenticationChannelInterceptor(authenticator);
        channel = mock(MessageChannel.class);
    }

    @Test
    void authenticatesConnectBearerTokenAndSetsPrincipal() {
        JwtAuthenticationToken authentication = authentication(
                Instant.now().plusSeconds(900)
        );
        when(authenticator.authenticate("access-token"))
                .thenReturn(authentication);
        Message<byte[]> message = message(StompCommand.CONNECT, null);

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        assertThat(StompHeaderAccessor.wrap(message).getUser())
                .isSameAs(authentication);
        verify(authenticator).authenticate("access-token");
    }

    @Test
    void rejectsSendWhenConnectedJwtHasExpired() {
        Message<byte[]> message = message(
                StompCommand.SEND,
                authentication(Instant.now().minusSeconds(1))
        );

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(ChatAuthenticationException.class)
                .extracting(exception -> ((ChatAuthenticationException) exception)
                        .getErrorCode())
                .isEqualTo(ChatErrorCode.INVALID_ACCESS_TOKEN);
    }

    @Test
    void acceptsSubscribeWhileConnectedJwtIsValid() {
        Message<byte[]> message = message(
                StompCommand.SUBSCRIBE,
                authentication(Instant.now().plusSeconds(900))
        );

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    private Message<byte[]> message(
            StompCommand command,
            JwtAuthenticationToken authentication
    ) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("session-1");
        accessor.setUser(authentication);
        if (StompCommand.CONNECT.equals(command)) {
            accessor.setNativeHeader(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer access-token"
            );
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }

    private JwtAuthenticationToken authentication(Instant expiresAt) {
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject(USER_PUBLIC_ID)
                .issuedAt(expiresAt.minusSeconds(900))
                .expiresAt(expiresAt)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(), USER_PUBLIC_ID);
    }
}
