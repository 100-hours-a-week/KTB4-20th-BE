package com.planit.chat.websocket.subscription;

import com.planit.global.error.ErrorCode;
import org.springframework.messaging.MessagingException;

import java.util.Objects;

public class ChatSubscriptionException extends MessagingException {

    private final ErrorCode errorCode;

    public ChatSubscriptionException(ErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "오류 코드는 필수입니다.").getMessage());
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
