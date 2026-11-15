package com.planit.mq.message;

import com.planit.mq.job.AiJobType;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * AI 요청 메시지 본문(계약 문서 4.3절). jobId는 JavaScript 정밀도 문제를 피하려 문자열로,
 * deadlineAt은 초 단위 ISO-8601(+09:00)로 보낸다.
 *
 * @param request 작업별 기존 AI HTTP 요청 본문
 */
public record AiRequestEnvelope(
        int schemaVersion,
        String jobId,
        AiJobType jobType,
        String deadlineAt,
        Object request
) {

    public static final int SCHEMA_VERSION = 1;

    public static AiRequestEnvelope of(AiJobType jobType, Long jobId, OffsetDateTime deadlineAt, Object request) {
        return new AiRequestEnvelope(
                SCHEMA_VERSION,
                String.valueOf(jobId),
                jobType,
                deadlineAt.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                request
        );
    }
}
