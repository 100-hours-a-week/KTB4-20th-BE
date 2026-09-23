package com.planit.auth.config;

import com.planit.auth.dto.AccessTokenResponse;
import com.planit.auth.service.AuthCookieService;
import com.planit.auth.service.AuthService;
import com.planit.global.security.CustomAccessDeniedHandler;
import com.planit.global.security.CustomAuthenticationEntryPoint;
import com.planit.global.security.SecurityErrorResponseWriter;
import com.planit.user.controller.UserController;
import com.planit.user.dto.CurrentUserResponse;
import com.planit.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.net.URI;
import java.time.Duration;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        com.planit.auth.controller.AuthController.class,
        UserController.class
})
@Import({
        SecurityConfig.class,
        SecurityErrorResponseWriter.class,
        CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class,
        SecurityConfigTest.TestAuthPropertiesConfiguration.class
})
class SecurityConfigTest {

    private static final String USER_PUBLIC_ID =
            "01991f6e-7300-7b21-a3cc-1436db3df95e";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthCookieService authCookieService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void requiresAccessTokenForCurrentUser() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void allowsRefreshWithoutAccessToken() throws Exception {
        when(authService.refresh(null, null))
                .thenReturn(new AccessTokenResponse(
                        "access-token",
                        "Bearer",
                        900
                ));

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken")
                        .value("access-token"));
    }

    @Test
    void passesJwtSubjectToCurrentUserService() throws Exception {
        UUID publicId = UUID.fromString(USER_PUBLIC_ID);
        when(userService.getCurrentUser(USER_PUBLIC_ID))
                .thenReturn(new CurrentUserResponse(
                        publicId,
                        "플랜잇사용자",
                        null
                ));

        mockMvc.perform(get("/api/users/me")
                        .with(jwt().jwt(token ->
                                token.subject(USER_PUBLIC_ID)
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.publicId")
                        .value(USER_PUBLIC_ID))
                .andExpect(jsonPath("$.data.userName")
                        .value("플랜잇사용자"));
    }

    @Test
    void allowsConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/refresh")
                        .header(
                                HttpHeaders.ORIGIN,
                                "http://localhost:5173"
                        )
                        .header(
                                HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                HttpMethod.POST.name()
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"
                ))
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                        "true"
                ));
    }

    @Test
    void allowsDefaultProfileImageWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/images/default-profile.svg"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE,
                        "image/svg+xml"
                ));
    }

    @Test
    void allowsWebSocketHandshakePathWithoutHttpAccessToken() throws Exception {
        mockMvc.perform(get("/ws"))
                .andExpect(status().isNotFound());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestAuthPropertiesConfiguration {

        @Bean
        AuthProperties authProperties() {
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
                            URI.create("http://localhost:8080/api/auth/oauth/callback"),
                            URI.create("https://kauth.kakao.com/oauth/authorize"),
                            URI.create("https://kauth.kakao.com/oauth/token"),
                            URI.create("https://kapi.kakao.com/v2/user/me"),
                            URI.create("https://kapi.kakao.com/v1/user/unlink")
                    )
            );
        }
    }
}
