package com.planit.chat.error;

public enum ChatErrorCode {

    STOMP_AUTHENTICATION_REQUIRED("STOMP CONNECT 인증이 필요합니다."),
    ACCESS_TOKEN_REQUIRED("Bearer Access Token이 필요합니다."),
    ACCESS_TOKEN_AUTHENTICATOR_UNAVAILABLE("Access Token 인증 구현이 연결되지 않았습니다."),
    INVALID_ACCESS_TOKEN("유효하지 않은 Access Token입니다.");

    private final String message;

    ChatErrorCode(String message) {
        this.message = message;
    }

    public String getCode() {
        return name();
    }

    public String getMessage() {
        return message;
    }
}
