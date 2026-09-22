package com.planit.global.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값을 확인해 주세요."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    REFRESH_TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED, "Refresh Token이 필요합니다."),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Refresh Token이 유효하지 않습니다."),
    USER_WITHDRAWN(HttpStatus.UNAUTHORIZED, "탈퇴 처리된 사용자입니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    ORIGIN_NOT_ALLOWED(HttpStatus.FORBIDDEN, "허용되지 않은 요청 출처입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    FILE_TOO_LARGE(HttpStatus.valueOf(413), "허용된 파일 크기를 초과했습니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 미디어 타입입니다."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청 횟수 제한을 초과했습니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "유효하지 않은 cursor입니다."),
    INVALID_AFTER_MESSAGE_ID(HttpStatus.BAD_REQUEST, "유효하지 않은 이후 메시지 기준값입니다."),
    INVALID_MESSAGE_HISTORY_QUERY(HttpStatus.BAD_REQUEST, "메시지 이력 조회 조건을 확인해 주세요."),
    INVALID_CHAT_MESSAGE_PAYLOAD(HttpStatus.BAD_REQUEST, "채팅 메시지 요청 값을 확인해 주세요."),
    CHAT_MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "채팅 메시지는 1,000자 이하여야 합니다."),
    CLIENT_MESSAGE_ID_REUSED(HttpStatus.CONFLICT, "이미 다른 메시지에 사용된 clientMessageId입니다."),
    REGIONAL_CHAT_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "지역 채팅방을 찾을 수 없습니다."),
    REGIONAL_CHAT_MEMBER_REQUIRED(HttpStatus.FORBIDDEN, "지역 채팅방 참여가 필요합니다."),
    CHAT_POLICY_CONSENT_REQUIRED(HttpStatus.CONFLICT, "현재 채팅 운영 정책에 동의해야 합니다."),
    TRIP_DATE_CONFLICT(HttpStatus.CONFLICT, "해당 날짜에 참여 중인 여행이 있습니다."),
    CHAT_POLICY_VERSION_NOT_ACTIVE(HttpStatus.CONFLICT, "현재 활성 채팅 운영 정책이 아닙니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "예상하지 못한 서버 오류가 발생했습니다."),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "서비스를 일시적으로 사용할 수 없습니다."),
    OAUTH_ACCESS_DENIED(HttpStatus.UNAUTHORIZED, "카카오 로그인이 취소되었습니다."),
    OAUTH_STATE_INVALID(HttpStatus.BAD_REQUEST, "OAuth state가 유효하지 않습니다."),
    OAUTH_CODE_EXCHANGE_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "카카오 인가 코드 교환에 실패했습니다."),
    OAUTH_USER_INFO_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "카카오 사용자 정보를 가져오지 못했습니다."),
    OAUTH_LOGIN_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "로그인 처리에 실패했습니다."),
    LOGOUT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "로그아웃을 완료하지 못했습니다."),
    KAKAO_UNLINK_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "카카오 연결 해제에 실패했습니다."),
    WITHDRAWAL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "회원 탈퇴를 완료하지 못했습니다."),
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
