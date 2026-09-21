package com.planit.global.security;

import com.planit.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityErrorHandlerTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final SecurityErrorResponseWriter responseWriter =
            new SecurityErrorResponseWriter(objectMapper);

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

    @Test
    void writerUsesStatusAndMessageFromErrorCode() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        responseWriter.write(response, ErrorCode.SERVICE_UNAVAILABLE);

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(body.get("code").asText()).isEqualTo("SERVICE_UNAVAILABLE");
    }
}
