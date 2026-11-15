package com.planit.mq.result;

/** 결과 메시지 본문을 읽을 수 없다. 다시 처리해도 같으므로 재시도하지 않고 응답 DLQ로 보낸다. */
public class InvalidAiResultMessageException extends RuntimeException {

    public InvalidAiResultMessageException(String message) {
        super(message);
    }

    public InvalidAiResultMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
