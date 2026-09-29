package com.planit.global.security;

import com.planit.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityErrorHandlerTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final SecurityErrorResponseWriter responseWriter =
            new SecurityErrorResponseWriter(objectMapper);

    @DisplayName("인증되지 않은 요청에 공통 인증 오류 응답을 작성한다")
    @Test
    void authenticationEntryPointWritesCommonUnauthorizedResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        CustomAuthenticationEntryPoint entryPoint =
                new CustomAuthenticationEntryPoint(responseWriter);

        entryPoint.commence(
                new MockHttpServletRequest(),
                response,
                new InsufficientAuthenticationException("인증 필요")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.get("code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(body.get("message").asText()).isEqualTo("인증이 필요합니다.");
        assertThat(body.get("data").isNull()).isTrue();
    }

    @DisplayName("탈퇴한 사용자의 요청에 전용 인증 오류 응답을 작성한다")
    @Test
    void authenticationEntryPointWritesWithdrawnUserResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        CustomAuthenticationEntryPoint entryPoint =
                new CustomAuthenticationEntryPoint(responseWriter);
        JwtValidationException validationException =
                new JwtValidationException(
                        "활성 사용자가 아닙니다.",
                        java.util.List.of(new OAuth2Error(
                                "user_withdrawn"
                        ))
                );

        entryPoint.commence(
                new MockHttpServletRequest(),
                response,
                new InvalidBearerTokenException(
                        "JWT가 유효하지 않습니다.",
                        validationException
                )
        );

        JsonNode body = objectMapper.readTree(
                response.getContentAsString()
        );
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body.get("code").asText())
                .isEqualTo("USER_WITHDRAWN");
    }

    @DisplayName("권한이 없는 요청에 공통 접근 거부 응답을 작성한다")
    @Test
    void accessDeniedHandlerWritesCommonForbiddenResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        CustomAccessDeniedHandler handler = new CustomAccessDeniedHandler(responseWriter);

        handler.handle(
                new MockHttpServletRequest(),
                response,
                new AccessDeniedException("접근 거부")
        );

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.get("code").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(body.get("message").asText()).isEqualTo("접근 권한이 없습니다.");
        assertThat(body.get("data").isNull()).isTrue();
    }

    @DisplayName("보안 오류 응답에 오류 코드의 상태와 메시지를 사용한다")
    @Test
    void writerUsesStatusAndMessageFromErrorCode() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        responseWriter.write(response, ErrorCode.SERVICE_UNAVAILABLE);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(body.get("code").asText()).isEqualTo("SERVICE_UNAVAILABLE");
    }
}
