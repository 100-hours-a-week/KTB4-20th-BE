package com.planit.mq.message;

import com.planit.mq.job.AiJobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiRequestMessageWriterTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AiRequestMessageWriter writer = new AiRequestMessageWriter(jsonMapper);

    @DisplayName("계약 4.3절 형식으로 jobId는 문자열, deadlineAt은 초 단위 +09:00으로 쓰고 요청 본문은 그대로 넣는다")
    @Test
    void writesContractEnvelope() {
        OffsetDateTime deadlineAt = OffsetDateTime.of(2026, 11, 12, 6, 0, 0, 123_000_000, ZoneOffset.ofHours(9));

        JsonNode json = jsonMapper.readTree(writer.write(
                AiJobType.PHOTO_MISSION_GENERATE,
                9_007_199_254_740_993L,
                deadlineAt,
                Map.of("itinerary_id", "trip_7", "places", List.of())
        ));

        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.get("jobId").isString()).isTrue();
        assertThat(json.get("jobId").asString()).isEqualTo("9007199254740993");
        assertThat(json.get("jobType").asString()).isEqualTo("PHOTO_MISSION_GENERATE");
        assertThat(json.get("deadlineAt").asString()).isEqualTo("2026-11-12T06:00:00+09:00");
        assertThat(json.get("request").get("itinerary_id").asString()).isEqualTo("trip_7");
    }
}
