package com.planit.mq.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.planit.mq.job.AiJobType;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 결과 메시지 본문을 읽고 계약에 맞는지 확인한다. */
@Component
public class AiResultEnvelopeParser {

    static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public AiResultEnvelopeParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @throws InvalidAiResultMessageException JSON이 아니거나, 필수 값이 없거나, 지원하지 않는 값이면
     */
    public AiResultEnvelope parse(byte[] body) {
        RawEnvelope raw = read(body);
        requireSchemaVersion(raw.schemaVersion());
        AiResultStatus status = enumOf(AiResultStatus.class, raw.status(), "status");
        requireOutcome(status, raw);
        return new AiResultEnvelope(
                raw.schemaVersion(),
                jobIdOf(raw.jobId()),
                enumOf(AiJobType.class, raw.jobType(), "jobType"),
                requireText(raw.requestMessageId(), "requestMessageId"),
                status,
                raw.errorKind(),
                isMissing(raw.result()) ? null : raw.result()
        );
    }

    private RawEnvelope read(byte[] body) {
        try {
            RawEnvelope raw = objectMapper.readValue(body, RawEnvelope.class);
            if (raw == null) {
                throw new InvalidAiResultMessageException("결과 메시지 본문이 비어 있음");
            }
            return raw;
        } catch (JacksonException e) {
            throw new InvalidAiResultMessageException("결과 메시지를 JSON으로 읽지 못함", e);
        }
    }

    private static void requireSchemaVersion(Integer schemaVersion) {
        if (schemaVersion == null || schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new InvalidAiResultMessageException("지원하지 않는 schemaVersion=" + schemaVersion);
        }
    }

    /** SUCCEEDED면 결과 본문이, FAILED면 실패 원인이 있어야 한다. */
    private static void requireOutcome(AiResultStatus status, RawEnvelope raw) {
        if (status == AiResultStatus.SUCCEEDED && isMissing(raw.result())) {
            throw new InvalidAiResultMessageException("성공 결과에 result가 없음");
        }
        if (status == AiResultStatus.FAILED) {
            requireText(raw.errorKind(), "errorKind");
        }
    }

    private static Long jobIdOf(String jobId) {
        String value = requireText(jobId, "jobId");
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            throw new InvalidAiResultMessageException("jobId가 숫자가 아님 jobId=" + value, e);
        }
    }

    private static <E extends Enum<E>> E enumOf(Class<E> type, String value, String field) {
        String name = requireText(value, field);
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            throw new InvalidAiResultMessageException("지원하지 않는 " + field + "=" + name, e);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidAiResultMessageException(field + " 값이 없음");
        }
        return value;
    }

    private static boolean isMissing(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawEnvelope(
            Integer schemaVersion,
            String jobId,
            String jobType,
            String requestMessageId,
            String status,
            String errorKind,
            JsonNode result
    ) {
    }
}
