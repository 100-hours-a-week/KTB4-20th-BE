package com.planit.mq.result;

import com.planit.mq.job.AiJobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiResultEnvelopeParserTest {

    private final AiResultEnvelopeParser parser = new AiResultEnvelopeParser(JsonMapper.builder().build());

    @DisplayName("성공 결과의 작업 정보와 작업별 결과 본문을 읽는다")
    @Test
    void parsesSucceededResult() {
        AiResultEnvelope envelope = parser.parse(bytes("""
                {"schemaVersion":1,"jobId":"9007199254740993","jobType":"SCHEDULE_GENERATE",
                 "requestMessageId":"0192f3a1-7c2e-7b1a-9d3e-2f4a5b6c7d8e","status":"SUCCEEDED",
                 "errorKind":null,"result":{"status_code":200,"data":{"routes":[]}},"extra":"무시"}
                """));

        assertThat(envelope.jobId()).isEqualTo(9_007_199_254_740_993L);
        assertThat(envelope.jobType()).isEqualTo(AiJobType.SCHEDULE_GENERATE);
        assertThat(envelope.requestMessageId()).isEqualTo("0192f3a1-7c2e-7b1a-9d3e-2f4a5b6c7d8e");
        assertThat(envelope.isSucceeded()).isTrue();
        assertThat(envelope.errorKind()).isNull();
        assertThat(envelope.result().get("status_code").asInt()).isEqualTo(200);
    }

    @DisplayName("실패 결과는 실패 원인을 읽고 결과 본문은 비워 둔다")
    @Test
    void parsesFailedResult() {
        AiResultEnvelope envelope = parser.parse(bytes("""
                {"schemaVersion":1,"jobId":"7","jobType":"PHOTO_MISSION_GENERATE","requestMessageId":"m-1",
                 "status":"FAILED","errorKind":"AI_UNAVAILABLE","result":null}
                """));

        assertThat(envelope.status()).isEqualTo(AiResultStatus.FAILED);
        assertThat(envelope.errorKind()).isEqualTo("AI_UNAVAILABLE");
        assertThat(envelope.result()).isNull();
    }

    @DisplayName("계약에 맞지 않는 결과 메시지는 읽지 않는다")
    @ParameterizedTest
    @ValueSource(strings = {
            "not json",
            "",
            "null",
            // 지원하지 않는 schemaVersion
            """
            {"schemaVersion":2,"jobId":"1","jobType":"SCHEDULE_GENERATE","requestMessageId":"m","status":"SUCCEEDED","result":{}}""",
            // jobId가 숫자가 아님
            """
            {"schemaVersion":1,"jobId":"abc","jobType":"SCHEDULE_GENERATE","requestMessageId":"m","status":"SUCCEEDED","result":{}}""",
            // 모르는 jobType
            """
            {"schemaVersion":1,"jobId":"1","jobType":"UNKNOWN","requestMessageId":"m","status":"SUCCEEDED","result":{}}""",
            // requestMessageId 없음
            """
            {"schemaVersion":1,"jobId":"1","jobType":"SCHEDULE_GENERATE","status":"SUCCEEDED","result":{}}""",
            // 성공인데 result 없음
            """
            {"schemaVersion":1,"jobId":"1","jobType":"SCHEDULE_GENERATE","requestMessageId":"m","status":"SUCCEEDED","result":null}""",
            // 실패인데 errorKind 없음
            """
            {"schemaVersion":1,"jobId":"1","jobType":"SCHEDULE_GENERATE","requestMessageId":"m","status":"FAILED"}""",
            // 모르는 status
            """
            {"schemaVersion":1,"jobId":"1","jobType":"SCHEDULE_GENERATE","requestMessageId":"m","status":"DONE","result":{}}"""
    })
    void rejectsInvalidMessage(String body) {
        assertThatThrownBy(() -> parser.parse(bytes(body)))
                .isInstanceOf(InvalidAiResultMessageException.class);
    }

    private static byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }
}
