package com.planit.mq.message;

import com.planit.mq.job.AiJobType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;

/** Outbox에 저장할 AI 요청 메시지 본문을 JSON으로 만든다. */
@Component
public class AiRequestMessageWriter {

    private final ObjectMapper objectMapper;

    public AiRequestMessageWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(AiJobType jobType, Long jobId, OffsetDateTime deadlineAt, Object request) {
        return objectMapper.writeValueAsString(AiRequestEnvelope.of(jobType, jobId, deadlineAt, request));
    }
}
