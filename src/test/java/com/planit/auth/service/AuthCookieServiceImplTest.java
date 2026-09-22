package com.planit.auth.service;

import com.planit.auth.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieServiceImplTest {

    private AuthCookieServiceImpl authCookieService;

    @BeforeEach
    void setUp() {
        AuthProperties properties = new AuthProperties(
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
                                "https://kauth.kakao.com"
                                        + "/oauth/authorize"
                        ),
                        URI.create(
                                "https://kauth.kakao.com"
                                        + "/oauth/token"
                        ),
                        URI.create(
                                "https://kapi.kakao.com"
                                        + "/v2/user/me"
                        ),
                        URI.create(
                                "https://kapi.kakao.com"
                                        + "/v1/user/unlink"
                        )
                )
        );

        authCookieService =
                new AuthCookieServiceImpl(properties);
    }

    @Test
    void createsRefreshTokenCookie() {
        ResponseCookie cookie =
                authCookieService.createRefreshTokenCookie(
                        "test-refresh-token"
                );

        assertThat(cookie.getName())
                .isEqualTo("refresh_token");
        assertThat(cookie.getValue())
                .isEqualTo("test-refresh-token");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isFalse();
        assertThat(cookie.getPath())
                .isEqualTo("/api/auth");
        assertThat(cookie.getSameSite())
                .isEqualTo("Lax");
        assertThat(cookie.getMaxAge())
                .isEqualTo(Duration.ofDays(30));
    }

    @Test
    void deletesRefreshTokenCookie() {
        ResponseCookie cookie =
                authCookieService.deleteRefreshTokenCookie();

        assertThat(cookie.getName())
                .isEqualTo("refresh_token");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getPath())
                .isEqualTo("/api/auth");
        assertThat(cookie.getMaxAge())
                .isEqualTo(Duration.ZERO);
    }

    @Test
    void createsOAuthStateCookie() {
        ResponseCookie cookie =
                authCookieService.createOAuthStateCookie(
                        "test-oauth-state"
                );

        assertThat(cookie.getName())
                .isEqualTo("planit_oauth_state");
        assertThat(cookie.getValue())
                .isEqualTo("test-oauth-state");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath())
                .isEqualTo("/api/auth/oauth");
        assertThat(cookie.getMaxAge())
                .isEqualTo(Duration.ofMinutes(10));
    }
}
