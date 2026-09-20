package com.planit.global.security;

import com.planit.global.error.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponseWriter responseWriter;

    public CustomAuthenticationEntryPoint(SecurityErrorResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException
    ) throws IOException, ServletException {
        ErrorCode errorCode = isWithdrawnUser(authenticationException)
                ? ErrorCode.USER_WITHDRAWN
                : ErrorCode.AUTHENTICATION_REQUIRED;
        responseWriter.write(response, errorCode);
    }

    private boolean isWithdrawnUser(Throwable exception) {
        Throwable current = exception;

        while (current != null) {
            if (current instanceof JwtValidationException validationException
                    && validationException.getErrors().stream()
                    .anyMatch(error -> "user_withdrawn".equals(
                            error.getErrorCode()
                    ))) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }
}
