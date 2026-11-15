package com.planit.mq.result;

/** AI 처리 결과. 판정 결과가 "미션 실패"여도 판정 자체를 마쳤으면 SUCCEEDED다. */
public enum AiResultStatus {
    SUCCEEDED,
    FAILED
}
