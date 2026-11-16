package com.planit.photomission.service;

/**
 * 포토미션을 만들 수 없는 상태라 다시 처리해도 결과가 같다. AI 결과가 일정 장소와 맞지 않거나,
 * 확정 일정이나 방문 장소가 없는 경우다. 메시지 큐 결과 반영은 이 예외를 받으면 재시도하지 않고 작업을 실패로 끝낸다.
 */
public class PhotoMissionGenerationRejectedException extends RuntimeException {

    /** AI 결과가 일정 장소와 맞지 않음 */
    public static final String INVALID_AI_RESULT = "INVALID_AI_RESULT";
    /** 확정 일정이나 방문 장소가 없음 */
    public static final String MISSION_TARGET_NOT_FOUND = "MISSION_TARGET_NOT_FOUND";

    private final String errorCode;

    public PhotoMissionGenerationRejectedException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PhotoMissionGenerationRejectedException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
