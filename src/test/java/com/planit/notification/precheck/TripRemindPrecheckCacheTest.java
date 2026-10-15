package com.planit.notification.precheck;

import com.planit.ai.AiPrecheckResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TripRemindPrecheckCacheTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @DisplayName("AI 응답 데이터에 PRECHECKED 상태를 추가해 Valkey JSON 모델로 변환한다")
    @Test
    void createsPrecheckedCacheFromAiResponse() {
        AiPrecheckResponse.Data data = precheckData();

        TripRemindPrecheckCache cache = TripRemindPrecheckCache.from(data);
        JsonNode json = objectMapper.valueToTree(cache);

        assertThat(json.get("generated_at").asText())
                .isEqualTo("2026-10-10T01:00:03Z");
        assertThat(json.get("status").asText()).isEqualTo("PRECHECKED");
        assertThat(json.get("forecasts")).hasSize(1);
        assertThat(cache.forecasts().getFirst().date().toString())
                .isEqualTo("2026-10-11");
        assertThat(cache.forecasts().getFirst().regions().getFirst().region())
                .isEqualTo("REGION-SEOUL");
        assertThat(json.get("forecasts").toString())
                .contains("\"has_issue\":true");
        assertThat(cache.forecasts().getFirst().regions().getFirst()
                .reasons().getFirst().detail())
                .isEqualTo("heavy_rain");
        assertThat(json.has("status_code")).isFalse();
    }

    @DisplayName("변환 후 AI 응답 목록이 바뀌어도 Valkey JSON 모델은 변경되지 않는다")
    @Test
    void defensivelyCopiesAiResponseLists() {
        List<AiPrecheckResponse.Reason> reasons = new ArrayList<>();
        reasons.add(new AiPrecheckResponse.Reason(
                "weather_risk",
                "heavy_rain"
        ));
        AiPrecheckResponse.Data data = new AiPrecheckResponse.Data(
                Instant.parse("2026-10-10T01:00:03Z"),
                List.of(new AiPrecheckResponse.Forecast(
                        LocalDate.of(2026, 10, 11),
                        List.of(new AiPrecheckResponse.RegionForecast(
                                "REGION-SEOUL",
                                true,
                                reasons
                        ))
                ))
        );

        TripRemindPrecheckCache cache = TripRemindPrecheckCache.from(data);
        reasons.clear();

        assertThat(cache.forecasts().getFirst().regions().getFirst()
                .reasons()).hasSize(1);
        assertThatThrownBy(() -> cache.forecasts().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @DisplayName("AI 응답 데이터가 없으면 Valkey JSON 모델을 만들 수 없다")
    @Test
    void rejectsMissingAiResponseData() {
        assertThatThrownBy(() -> TripRemindPrecheckCache.from(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("AI 외부 요인 사전 점검 데이터는 필수입니다");
    }

    private static AiPrecheckResponse.Data precheckData() {
        return new AiPrecheckResponse.Data(
                Instant.parse("2026-10-10T01:00:03Z"),
                List.of(new AiPrecheckResponse.Forecast(
                        LocalDate.of(2026, 10, 11),
                        List.of(new AiPrecheckResponse.RegionForecast(
                                "REGION-SEOUL",
                                true,
                                List.of(new AiPrecheckResponse.Reason(
                                        "weather_risk",
                                        "heavy_rain"
                                ))
                        ))
                ))
        );
    }
}
