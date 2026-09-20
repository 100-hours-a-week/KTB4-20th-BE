package com.planit.auth.controller;

import com.planit.auth.config.AuthProperties;
import com.planit.auth.service.AuthCookieService;
import com.planit.auth.service.AuthService;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataAccessResourceFailureException;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @Test
    void redirectsAndDeletesOAuthCookiesWhenCallbackFails() {
        AuthService authService = mock(AuthService.class);
        AuthCookieService cookieService =
                mock(AuthCookieService.class);
        AuthProperties authProperties = mock(AuthProperties.class);
        when(authProperties.frontendBaseUrl())
                .thenReturn(URI.create("http://localhost:5173"));
        AuthController controller = new AuthController(
                authService,
                cookieService,
                authProperties
        );
        ResponseCookie deletedState = ResponseCookie
                .from("planit_oauth_state", "")
                .path("/api/auth/oauth")
                .maxAge(0)
                .build();
        ResponseCookie deletedReturnTo = ResponseCookie
                .from("planit_oauth_return_to", "")
                .path("/api/auth/oauth")
                .maxAge(0)
                .build();

        when(authService.login(
                "code",
                "wrong-state",
                null,
                "saved-state",
                "/"
        )).thenThrow(new BusinessException(
                ErrorCode.INVALID_REQUEST
        ));
        when(cookieService.deleteOAuthStateCookie())
                .thenReturn(deletedState);
        when(cookieService.deleteOAuthReturnToCookie())
                .thenReturn(deletedReturnTo);

        ResponseEntity<Void> response = controller.callback(
                "code",
                "wrong-state",
                null,
                "saved-state",
                "/"
        );

        assertThat(response.getStatusCode().value())
                .isEqualTo(302);
        assertThat(response.getHeaders().getLocation().toString())
                .isEqualTo(
                        "http://localhost:5173/login"
                                + "?error=INVALID_REQUEST"
                );
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .containsExactly(
                        deletedState.toString(),
                        deletedReturnTo.toString()
                );
    }

    @Test
    void redirectsWithLoginFailureWhenCallbackCommitFails() {
        AuthService authService = mock(AuthService.class);
        AuthCookieService cookieService =
                mock(AuthCookieService.class);
        AuthProperties authProperties = mock(AuthProperties.class);
        when(authProperties.frontendBaseUrl())
                .thenReturn(URI.create("http://localhost:5173"));
        AuthController controller = new AuthController(
                authService,
                cookieService,
                authProperties
        );
        ResponseCookie deletedState = ResponseCookie
                .from("planit_oauth_state", "")
                .path("/api/auth/oauth")
                .maxAge(0)
                .build();
        ResponseCookie deletedReturnTo = ResponseCookie
                .from("planit_oauth_return_to", "")
                .path("/api/auth/oauth")
                .maxAge(0)
                .build();

        when(authService.login(
                "code",
                "state",
                null,
                "state",
                "/"
        )).thenThrow(new DataAccessResourceFailureException(
                "commit failed"
        ));
        when(cookieService.deleteOAuthStateCookie())
                .thenReturn(deletedState);
        when(cookieService.deleteOAuthReturnToCookie())
                .thenReturn(deletedReturnTo);

        ResponseEntity<Void> response = controller.callback(
                "code",
                "state",
                null,
                "state",
                "/"
        );

        assertThat(response.getStatusCode().value())
                .isEqualTo(302);
        assertThat(response.getHeaders().getLocation().toString())
                .isEqualTo(
                        "http://localhost:5173/login"
                                + "?error=OAUTH_LOGIN_FAILED"
                );
        assertThat(response.getHeaders().get(HttpHeaders.SET_COOKIE))
                .containsExactly(
                        deletedState.toString(),
                        deletedReturnTo.toString()
                );
    }
}
