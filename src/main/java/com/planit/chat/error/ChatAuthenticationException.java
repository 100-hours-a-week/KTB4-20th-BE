package com.planit.chat.error;

import org.springframework.security.core.AuthenticationException;

import java.util.Objects;

public class ChatAuthenticationException extends AuthenticationException {

    private final ChatErrorCode errorCode;

    public ChatAuthenticationException(ChatErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode).getMessage());
        this.errorCode = errorCode;
    }

    public ChatAuthenticationException(
            ChatErrorCode errorCode,
            Throwable cause
    ) {
        super(Objects.requireNonNull(errorCode).getMessage(), cause);
        this.errorCode = errorCode;
    }

    public ChatErrorCode getErrorCode() {
        return errorCode;
    }
}
