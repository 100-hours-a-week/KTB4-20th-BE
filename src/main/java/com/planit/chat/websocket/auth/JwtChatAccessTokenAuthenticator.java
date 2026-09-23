package com.planit.chat.websocket.auth;

import com.planit.chat.error.ChatAuthenticationException;
import com.planit.chat.error.ChatErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtChatAccessTokenAuthenticator implements ChatAccessTokenAuthenticator {

    private final JwtDecoder jwtDecoder;

    @Override
    public Authentication authenticate(String accessToken) {    //HTTP와 달리 Selvlet Filter가 처리하지 못하기 때문에 직접 검증
        try {
            Jwt jwt = jwtDecoder.decode(accessToken);
            return new JwtAuthenticationToken(
                    jwt,
                    List.of(),
                    jwt.getSubject()
            );
        } catch (JwtException exception) {
            throw new ChatAuthenticationException(
                    ChatErrorCode.INVALID_ACCESS_TOKEN,
                    exception
            );
        }
    }
}
