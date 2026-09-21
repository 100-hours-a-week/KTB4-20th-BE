package com.planit.auth.token;

import com.planit.auth.config.AuthProperties;
import com.planit.auth.config.JwtConfig;
import com.planit.auth.dto.AccessTokenResponse;
import com.planit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import javax.crypto.SecretKey;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTokenProviderTest {

    private static final UUID USER_PUBLIC_ID = UUID.fromString(
            "01991f6e-7300-7b21-a3cc-1436db3df95e"
    );

    private JwtDecoder jwtDecoder;
    private JwtTokenProvider jwtTokenProvider;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        AuthProperties properties = createAuthProperties();
        JwtConfig jwtConfig = new JwtConfig();
        userRepository = mock(UserRepository.class);
        when(userRepository.existsByPublicIdAndDeletedAtIsNull(
                USER_PUBLIC_ID
        )).thenReturn(true);
        SecretKey secretKey = jwtConfig.jwtSecretKey(properties);
        JwtEncoder jwtEncoder = jwtConfig.jwtEncoder(secretKey);
        jwtDecoder = jwtConfig.jwtDecoder(
                secretKey,
                properties,
                userRepository
        );
        jwtTokenProvider = new JwtTokenProvider(
                jwtEncoder,
                properties
        );
    }

    @Test
    void issuesHs256AccessToken() {
        AccessTokenResponse response =
                jwtTokenProvider.issue(USER_PUBLIC_ID);

        Jwt jwt = jwtDecoder.decode(response.accessToken());

        assertThat(jwt.getHeaders().get("alg"))
                .hasToString("HS256");
        assertThat(jwt.getHeaders().get("typ"))
                .isEqualTo("at+jwt");
        assertThat(jwt.getClaimAsString("iss"))
                .isEqualTo("planit-auth");
        assertThat(jwt.getAudience())
                .containsExactly("planit-api");
        assertThat(jwt.getSubject())
                .isEqualTo(USER_PUBLIC_ID.toString());
        assertThat(jwt.getId()).isNotBlank();
        assertThat(Duration.between(
                jwt.getIssuedAt(),
                jwt.getExpiresAt()
        )).isEqualTo(Duration.ofMinutes(15));
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
    }

    @Test
    void rejectsAccessTokenForInactiveUser() {
        when(userRepository.existsByPublicIdAndDeletedAtIsNull(
                USER_PUBLIC_ID
        )).thenReturn(false);
        AccessTokenResponse response =
                jwtTokenProvider.issue(USER_PUBLIC_ID);

        assertThatThrownBy(() -> jwtDecoder.decode(
                response.accessToken()
        )).isInstanceOf(
                org.springframework.security.oauth2.jwt
                        .JwtValidationException.class
        );
    }

    private AuthProperties createAuthProperties() {
        return new AuthProperties(
                URI.create("http://localhost:5173"),
                URI.create("http://localhost:5173"),
                false,
                "planit_oauth_state",
                Duration.ofMinutes(10),
                new AuthProperties.Jwt(
                        "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
                        "planit-auth",
                        "planit-api",
                        Duration.ofMinutes(15)
                ),
                new AuthProperties.Kakao(
                        "test-client-id",
                        "test-client-secret",
                        "test-admin-key",
                        URI.create(
                                "http://localhost:8080"
                                        + "/api/auth/oauth/callback"
                        ),
                        URI.create(
                                "https://kauth.kakao.com/oauth/authorize"
                        ),
                        URI.create(
                                "https://kauth.kakao.com/oauth/token"
                        ),
                        URI.create(
                                "https://kapi.kakao.com/v2/user/me"
                        ),
                        URI.create(
                                "https://kapi.kakao.com/v1/user/unlink"
                        )
                )
        );
    }
}
