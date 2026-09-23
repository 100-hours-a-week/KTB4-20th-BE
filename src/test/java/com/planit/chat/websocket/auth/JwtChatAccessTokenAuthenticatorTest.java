package com.planit.chat.websocket.auth;

import com.planit.chat.error.ChatAuthenticationException;
import com.planit.chat.error.ChatErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtChatAccessTokenAuthenticatorTest {

    private static final String USER_PUBLIC_ID =
            "01991f6e-7300-7b21-a3cc-1436db3df95e";

    @Test
    void createsAuthenticatedPrincipalFromJwtSubject() {
        JwtDecoder jwtDecoder = mock(JwtDecoder.class);
        Jwt jwt = jwt(Instant.now().plusSeconds(900));
        when(jwtDecoder.decode("access-token")).thenReturn(jwt);
        JwtChatAccessTokenAuthenticator authenticator =
                new JwtChatAccessTokenAuthenticator(jwtDecoder);

        var authentication = authenticator.authenticate("access-token");

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo(USER_PUBLIC_ID);
        assertThat(authentication.getPrincipal()).isSameAs(jwt);
    }

    @Test
    void convertsJwtValidationFailureToChatAuthenticationFailure() {
        JwtDecoder jwtDecoder = mock(JwtDecoder.class);
        when(jwtDecoder.decode("invalid-token")).thenThrow(
                new JwtValidationException(
                        "invalid",
                        List.of(new OAuth2Error("invalid_token"))
                )
        );
        JwtChatAccessTokenAuthenticator authenticator =
                new JwtChatAccessTokenAuthenticator(jwtDecoder);

        assertThatThrownBy(() -> authenticator.authenticate("invalid-token"))
                .isInstanceOf(ChatAuthenticationException.class)
                .extracting(exception -> ((ChatAuthenticationException) exception)
                        .getErrorCode())
                .isEqualTo(ChatErrorCode.INVALID_ACCESS_TOKEN);
    }

    private Jwt jwt(Instant expiresAt) {
        Instant issuedAt = expiresAt.minusSeconds(900);
        return Jwt.withTokenValue("access-token")
                .header("alg", "HS256")
                .subject(USER_PUBLIC_ID)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
    }
}
