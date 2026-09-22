package com.planit.auth.service;

import com.planit.auth.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthCookieServiceImpl
        implements AuthCookieService {

    private static final String OAUTH_RETURN_TO_COOKIE =
            "planit_oauth_return_to";

    private static final String REFRESH_TOKEN_COOKIE =
            "refresh_token";

    private static final String OAUTH_COOKIE_PATH =
            "/api/auth/oauth";

    private static final String REFRESH_TOKEN_COOKIE_PATH =
            "/api/auth";

    private static final Duration REFRESH_TOKEN_MAX_AGE =
            Duration.ofDays(30);

    private final AuthProperties authProperties;

    @Override
    public ResponseCookie createOAuthStateCookie(
            String state
    ) {
        return createOAuthCookie(
                authProperties.oauthStateCookieName(),
                state,
                authProperties.oauthStateCookieMaxAge()
        );
    }

    @Override
    public ResponseCookie createOAuthReturnToCookie(
            String returnTo
    ) {
        return createOAuthCookie(
                OAUTH_RETURN_TO_COOKIE,
                returnTo,
                authProperties.oauthStateCookieMaxAge()
        );
    }

    @Override
    public ResponseCookie deleteOAuthStateCookie() {
        return createOAuthCookie(
                authProperties.oauthStateCookieName(),
                "",
                Duration.ZERO
        );
    }

    @Override
    public ResponseCookie deleteOAuthReturnToCookie() {
        return createOAuthCookie(
                OAUTH_RETURN_TO_COOKIE,
                "",
                Duration.ZERO
        );
    }

    @Override
    public ResponseCookie createRefreshTokenCookie(
            String refreshToken
    ) {
        return ResponseCookie
                .from(
                        REFRESH_TOKEN_COOKIE,
                        refreshToken
                )
                .httpOnly(true)
                .secure(authProperties.cookieSecure())
                .sameSite("Lax")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(REFRESH_TOKEN_MAX_AGE)
                .build();
    }

    @Override
    public ResponseCookie deleteRefreshTokenCookie() {
        return ResponseCookie
                .from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(authProperties.cookieSecure())
                .sameSite("Lax")
                .path(REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    private ResponseCookie createOAuthCookie(
            String name,
            String value,
            Duration maxAge
    ) {
        return ResponseCookie
                .from(name, value)
                .httpOnly(true)
                .secure(authProperties.cookieSecure())
                .sameSite("Lax")
                .path(OAUTH_COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}