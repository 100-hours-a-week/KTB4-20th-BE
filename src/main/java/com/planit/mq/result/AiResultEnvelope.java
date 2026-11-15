package com.planit.mq.result;

import com.planit.mq.job.AiJobType;
import tools.jackson.databind.JsonNode;

/**
 * AI Worker가 응답 큐로 보낸 결과 메시지 본문. 형식은 RabbitMQ 인프라·메시지 계약 문서 4.4절과 같다.
 *
 * @param requestMessageId 처리한 요청 메시지의 `message_id`
 * @param errorKind        FAILED일 때 실패 원인 (계약 문서 4.6절). 작업 테이블 `error_code`에 저장한다.
 * @param result           SUCCEEDED일 때 작업별 AI 응답 본문. 기존 동기 HTTP API 응답과 같은 형식이다.
 */
public record AiResultEnvelope(
        int schemaVersion,
        Long jobId,
        AiJobType jobType,
        String requestMessageId,
        AiResultStatus status,
        String errorKind,
        JsonNode result
) {

    public boolean isSucceeded() {
        return status == AiResultStatus.SUCCEEDED;
    }
}
