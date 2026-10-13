package com.planit.photomission.ai;

import com.planit.photomission.domain.MissionPhoto;
import com.planit.photomission.domain.PhotoEvaluation;
import com.planit.photomission.domain.PhotoEvaluationReason;
import com.planit.photomission.domain.PhotoEvaluationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PhotoMissionAiContractTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 10, 10, 12, 0);

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final PhotoMissionEvaluationMapper mapper =
            new PhotoMissionEvaluationMapper(objectMapper);

    @DisplayName("사진 판정 요청을 AI snake_case 계약으로 직렬화한다")
    @Test
    void serializesVerifyRequestContract() {
        PhotoMissionVerifyRequest request = new PhotoMissionVerifyRequest(
                "https://bucket.s3.ap-northeast-2.amazonaws.com/photo.jpg",
                "place_abc",
                "첨성대",
                new PhotoMissionVerifyRequest.Coordinates(
                        BigDecimal.valueOf(35.8347),
                        BigDecimal.valueOf(129.2190)
                ),
                "첨성대 돌 가까이 찍기",
                null
        );

        JsonNode json = objectMapper.valueToTree(request);

        assertThat(json.get("image_url").asText())
                .isEqualTo(request.imageUrl());
        assertThat(json.get("place_id").asText()).isEqualTo("place_abc");
        assertThat(json.get("place_name").asText()).isEqualTo("첨성대");
        assertThat(json.get("place_coordinates").get("latitude").decimalValue())
                .isEqualByComparingTo("35.8347");
        assertThat(json.get("mission_description").asText())
                .isEqualTo("첨성대 돌 가까이 찍기");
        assertThat(json.get("photo_coordinates").isNull()).isTrue();
    }

    @DisplayName("AI retry 응답을 BE 판정 값으로 명시적으로 매핑한다")
    @Test
    void mapsRetryResponse() throws Exception {
        PhotoMissionVerifyResponse response = objectMapper.readValue("""
                {
                  "result": "retry",
                  "reason": null,
                  "match_score": 75.0,
                  "detected_labels": [
                    {"name": "석탑", "matched": true},
                    {"name": "사람", "matched": false}
                  ],
                  "landmark_confidence": 92.0,
                  "retry_hint": "탑이 사람에 가려졌어요."
                }
                """, PhotoMissionVerifyResponse.class);
        PhotoEvaluation evaluation = runningEvaluation();

        mapper.apply(evaluation, response, NOW.plusSeconds(1));

        assertThat(evaluation.getResult())
                .isEqualTo(PhotoEvaluationResult.RETRY);
        assertThat(evaluation.getReason()).isNull();
        assertThat(evaluation.getMatchScore()).isEqualByComparingTo("75.0");
        assertThat(objectMapper.readTree(evaluation.getDetectedLabelsJson()))
                .hasSize(2);
        assertThat(evaluation.getLandmarkConfidence())
                .isEqualByComparingTo("92.0");
        assertThat(evaluation.getRetryHint())
                .isEqualTo("탑이 사람에 가려졌어요.");
    }

    @DisplayName("AI 위치 불일치 응답을 FAIL과 LOCATION_MISMATCH로 매핑한다")
    @Test
    void mapsLocationMismatchResponse() {
        PhotoMissionVerifyResponse response = new PhotoMissionVerifyResponse(
                "fail",
                "location_mismatch",
                BigDecimal.ZERO,
                List.of(),
                null,
                null
        );
        PhotoEvaluation evaluation = runningEvaluation();

        mapper.apply(evaluation, response, NOW.plusSeconds(1));

        assertThat(evaluation.getResult())
                .isEqualTo(PhotoEvaluationResult.FAIL);
        assertThat(evaluation.getReason())
                .isEqualTo(PhotoEvaluationReason.LOCATION_MISMATCH);
        assertThat(evaluation.getDetectedLabelsJson()).isEqualTo("[]");
    }

    @DisplayName("계약에 없는 AI 판정 문자열은 거부한다")
    @Test
    void rejectsUnknownResult() {
        PhotoMissionVerifyResponse response = new PhotoMissionVerifyResponse(
                "unknown",
                null,
                BigDecimal.TEN,
                List.of(),
                null,
                null
        );

        assertThatThrownBy(() -> mapper.apply(
                runningEvaluation(), response, NOW.plusSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private PhotoEvaluation runningEvaluation() {
        return PhotoEvaluation.start(mock(MissionPhoto.class), 1, NOW);
    }
}
