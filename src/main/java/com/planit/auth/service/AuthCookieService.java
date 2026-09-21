package com.planit.auth.service;

import org.springframework.http.ResponseCookie;

public interface AuthCookieService {

    ResponseCookie createOAuthStateCookie(String state);

    ResponseCookie createOAuthReturnToCookie(String returnTo);

    ResponseCookie deleteOAuthStateCookie();

    ResponseCookie deleteOAuthReturnToCookie();

    ResponseCookie createRefreshTokenCookie(String refreshToken);

    ResponseCookie deleteRefreshTokenCookie();
}