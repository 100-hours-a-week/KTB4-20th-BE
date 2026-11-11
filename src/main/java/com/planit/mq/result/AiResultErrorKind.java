package com.planit.mq.result;

/** 결과 메시지를 응답 DLQ로 보낼 때 붙이는 `x-error-kind` 값. */
public enum AiResultErrorKind {

    /** 본문을 읽을 수 없거나 계약에 맞지 않음 (계약 문서 4.6절과 같은 값) */
    INVALID_MESSAGE,
    /** 메시지는 정상이지만 그 작업 종류의 결과 Handler가 없음 */
    UNSUPPORTED_JOB_TYPE,
    /** 결과 반영 중 일시 오류가 Result Retry Queue 재시도를 모두 쓴 뒤에도 계속됨 */
    RESULT_PROCESSING_FAILED
}
