package com.planit.auth.controller;

import com.planit.auth.dto.AccessTokenResponse;
import com.planit.auth.dto.OAuthAuthorizeResult;
import com.planit.auth.dto.OAuthLoginResult;
import com.planit.auth.config.AuthProperties;
import com.planit.auth.service.AuthCookieService;
import com.planit.auth.service.AuthService;
import com.planit.global.error.BusinessException;
import com.planit.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String OAUTH_STATE_COOKIE =
            "planit_oauth_state";

    private static final String OAUTH_RETURN_TO_COOKIE =
            "planit_oauth_return_to";

    private static final String REFRESH_TOKEN_COOKIE =
            "refresh_token";

    private final AuthService authService;
    private final AuthCookieService authCookieService;
    private final AuthProperties authProperties;

    @GetMapping("/oauth/authorize")
    public ResponseEntity<Void> authorize(
            @RequestParam(defaultValue = "/") String returnTo
    ) {
        OAuthAuthorizeResult result =
                authService.createAuthorizationRequest(returnTo);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(result.authorizationUri());
        headers.add(
                HttpHeaders.SET_COOKIE,
                authCookieService
                        .createOAuthStateCookie(result.state())
                        .toString()
        );
        headers.add(
                HttpHeaders.SET_COOKIE,
                authCookieService
                        .createOAuthReturnToCookie(result.returnTo())
                        .toString()
        );

        return ResponseEntity.status(302)
                .headers(headers)
                .build();
    }

    @GetMapping("/oauth/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @CookieValue(
                    name = OAUTH_STATE_COOKIE,
                    required = false
            ) String savedState,
            @CookieValue(
                    name = OAUTH_RETURN_TO_COOKIE,
                    required = false
            ) String savedReturnTo
    ) {
        OAuthLoginResult result;

        try {
            result = authService.login(
                    code,
                    state,
                    error,
                    savedState,
                    savedReturnTo
            );
        } catch (BusinessException exception) {
            return oauthFailureResponse(
                    exception.getErrorCode().getCode()
            );
        } catch (DataAccessException exception) {
            return oauthFailureResponse(
                    "OAUTH_LOGIN_FAILED"
            );
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(result.redirectUri());
        headers.add(
                HttpHeaders.SET_COOKIE,
                authCookieService
                        .createRefreshTokenCookie(result.refreshToken())
                        .toString()
        );
        headers.add(
                HttpHeaders.SET_COOKIE,
                authCookieService
                        .deleteOAuthStateCookie()
                        .toString()
        );
        headers.add(
                HttpHeaders.SET_COOKIE,
                authCookieService
                        .deleteOAuthReturnToCookie()
                        .toString()
        );

        return ResponseEntity.status(302)
                .headers(headers)
                .build();
    }

    private ResponseEntity<Void> oauthFailureResponse(
            String errorCode
    ) {
        URI loginUri = UriComponentsBuilder
                .fromUri(authProperties.frontendBaseUrl())
                .path("/login")
                .queryParam("error", errorCode)
                .build()
                .encode()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(loginUri);
        headers.add(
                HttpHeaders.SET_COOKIE,
                authCookieService
                        .deleteOAuthStateCookie()
                        .toString()
        );
        headers.add(
                HttpHeaders.SET_COOKIE,
                authCookieService
                        .deleteOAuthReturnToCookie()
                        .toString()
        );

        return ResponseEntity.status(302)
                .headers(headers)
                .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AccessTokenResponse>> refresh(
            @CookieValue(
                    name = REFRESH_TOKEN_COOKIE,
                    required = false
            ) String refreshToken,
            @RequestHeader(
                    name = HttpHeaders.ORIGIN,
                    required = false
            ) String origin
    ) {
        AccessTokenResponse response =
                authService.refresh(refreshToken, origin);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(ApiResponse.success(
                        "ACCESS_TOKEN_ISSUED",
                        "Access Token이 발급되었습니다.",
                        response
                ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(
                    name = REFRESH_TOKEN_COOKIE,
                    required = false
            ) String refreshToken,
            @RequestHeader(
                    name = HttpHeaders.ORIGIN,
                    required = false
            ) String origin
    ) {
        authService.logout(refreshToken, origin);

        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        authCookieService
                                .deleteRefreshTokenCookie()
                                .toString()
                )
                .build();
    }
}
