package com.planit.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값을 확인해 주세요."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    FILE_TOO_LARGE(HttpStatus.valueOf(413), "허용된 파일 크기를 초과했습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 미디어 타입입니다."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청 횟수 제한을 초과했습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "유효하지 않은 cursor입니다."),
    REGIONAL_CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "지역 채팅방을 찾을 수 없습니다."),
    REGIONAL_CHAT_MEMBER_REQUIRED(HttpStatus.FORBIDDEN, "지역 채팅방 참여가 필요합니다."),
    CHAT_POLICY_CONSENT_REQUIRED(HttpStatus.CONFLICT, "현재 채팅 운영 정책에 동의해야 합니다."),
    CHAT_POLICY_VERSION_NOT_ACTIVE(HttpStatus.CONFLICT, "현재 활성 채팅 운영 정책이 아닙니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "예상하지 못한 서버 오류가 발생했습니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "서비스를 일시적으로 사용할 수 없습니다."),
    ACTIVE_CHAT_POLICY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "활성 채팅 운영 정책을 사용할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return name();
    }

    public String getMessage() {
        return message;
    }
}
