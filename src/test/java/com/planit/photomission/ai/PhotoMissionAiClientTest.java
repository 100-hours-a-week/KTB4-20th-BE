package com.planit.photomission.ai;

import com.planit.ai.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PhotoMissionAiClientTest {

    private MockRestServiceServer server;
    private PhotoMissionAiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PhotoMissionAiClient(
                builder.build(),
                new AiProperties(
                        URI.create("http://localhost:8000"),
                        "test-internal-token",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(30)
                )
        );
    }

    @DisplayName("AI 서버에 포토미션 사진 판정을 요청한다")
    @Test
    void requestsPhotoMissionVerificationFromAiServer() {
        PhotoMissionVerifyRequest request = new PhotoMissionVerifyRequest(
                "https://bucket.s3.ap-northeast-2.amazonaws.com/photo.jpg",
                "place_abc",
                "첨성대",
                new PhotoMissionVerifyRequest.Coordinates(
                        BigDecimal.valueOf(35.8347),
                        BigDecimal.valueOf(129.2190)
                ),
                "첨성대 돌 가까이 찍기",
                new PhotoMissionVerifyRequest.Coordinates(
                        BigDecimal.valueOf(35.8350),
                        BigDecimal.valueOf(129.2188)
                )
        );

        server.expect(once(), requestTo(
                        "http://localhost:8000/photomissions/31/verify"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        "X-Internal-Token",
                        "test-internal-token"
                ))
                .andExpect(content().json("""
                        {
                          "image_url": "https://bucket.s3.ap-northeast-2.amazonaws.com/photo.jpg",
                          "place_id": "place_abc",
                          "place_name": "첨성대",
                          "place_coordinates": {
                            "latitude": 35.8347,
                            "longitude": 129.2190
                          },
                          "mission_description": "첨성대 돌 가까이 찍기",
                          "photo_coordinates": {
                            "latitude": 35.8350,
                            "longitude": 129.2188
                          }
                        }
                        """))
                .andRespond(withSuccess("""
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
                        """, MediaType.APPLICATION_JSON));

        PhotoMissionVerifyResponse response = client.verify(31L, request);

        assertThat(response.result()).isEqualTo("retry");
        assertThat(response.matchScore()).isEqualByComparingTo("75.0");
        assertThat(response.detectedLabels()).hasSize(2);
        assertThat(response.landmarkConfidence())
                .isEqualByComparingTo("92.0");
        assertThat(response.retryHint())
                .isEqualTo("탑이 사람에 가려졌어요.");
        server.verify();
    }

    @DisplayName("AI 서버에 전체 여행의 포토미션 생성을 요청한다")
    @Test
    void requestsPhotoMissionGenerationFromAiServer() {
        PhotoMissionGenerateRequest request =
                new PhotoMissionGenerateRequest(
                        "trip_1",
                        List.of(new PhotoMissionGenerateRequest.Place(
                                "place_abc",
                                new PhotoMissionGenerateRequest.DisplayName(
                                        "첨성대",
                                        "ko"
                                ),
                                List.of("Day 1"),
                                List.of("역사·유적")
                        ))
                );

        server.expect(once(), requestTo(
                        "http://localhost:8000/photomissions/generate"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        "X-Internal-Token",
                        "test-internal-token"
                ))
                .andExpect(content().json("""
                        {
                          "itinerary_id": "trip_1",
                          "places": [{
                            "id": "place_abc",
                            "displayName": {
                              "text": "첨성대",
                              "languageCode": "ko"
                            },
                            "selected_for": ["Day 1"],
                            "matched_preferences": ["역사·유적"]
                          }]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "missions": [{
                            "mission_id": "mission_abc",
                            "place_id": "place_abc",
                            "description": "첨성대 전경을 찍어보세요.",
                            "scope": "PERSONAL",
                            "primary_category": "역사·유적"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        PhotoMissionGenerateResponse response = client.generate(request);

        assertThat(response.missions()).hasSize(1);
        assertThat(response.missions().getFirst().placeId())
                .isEqualTo("place_abc");
        server.verify();
    }
}
