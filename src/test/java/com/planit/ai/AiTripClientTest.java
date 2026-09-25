package com.planit.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiTripClientTest {

    private MockRestServiceServer server;
    private AiTripClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiTripClient(
                builder.build(),
                new AiProperties(
                        URI.create("http://localhost:8000"),
                        "test-internal-token",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(30)
                )
        );
    }

    @Test
    void requestsPlaceSelectionFromAiServer() {
        AiPlaceSelectionRequest request = new AiPlaceSelectionRequest(
                "제주",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 1),
                List.of(new AiPlaceSelectionRequest.MemberSurvey(
                        new AiPlaceSelectionRequest.User("user-1"),
                        List.of(
                                1, 2, 3, 4, 5,
                                1, 2, 3, 4, 5,
                                1, 2, 3, 4, 5
                        ),
                        List.of("해산물")
                ))
        );

        server.expect(once(), requestTo(
                        "http://localhost:8000/trips/place-selection"
                ))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        "X-Internal-Token",
                        "test-internal-token"
                ))
                .andExpect(content().json("""
                        {
                          "region": "제주",
                          "start_date": "2026-10-01",
                          "end_date": "2026-10-01",
                          "members": [
                            {
                              "user": {"user_id": "user-1"},
                              "survey_result": [
                                1, 2, 3, 4, 5,
                                1, 2, 3, 4, 5,
                                1, 2, 3, 4, 5
                              ],
                              "deal_breakers": ["해산물"]
                            }
                          ]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "status_code": 200,
                          "data": {
                            "places": [
                              {
                                "id": "ChIJT-dJpdYkcDURU4xGNLNrBdY",
                                "displayName": {
                                  "text": "경복궁",
                                  "languageCode": "ko"
                                },
                                "location": {
                                  "latitude": 37.5796,
                                  "longitude": 126.9770
                                },
                                "types": ["historical_landmark", "museum"],
                                "rating": 4.6,
                                "userRatingCount": 4820,
                                "editorialSummary": null,
                                "selected_for": ["user_id_1", "user_id_3"],
                                "matched_preferences": ["HISTORY_CULTURE"]
                              },
                              {
                                "id": "ChIJIbLB8yA7cDURFdE4_Gwn86M",
                                "displayName": {
                                  "text": "북촌한옥마을",
                                  "languageCode": "ko"
                                },
                                "location": {
                                  "latitude": 37.5826,
                                  "longitude": 126.9831
                                },
                                "types": [
                                  "historical_landmark",
                                  "tourist_attraction"
                                ],
                                "rating": 4.4,
                                "userRatingCount": 3105,
                                "editorialSummary": {
                                  "text": "전통 한옥이 밀집된 서울의 대표적인 역사 마을.",
                                  "languageCode": "ko"
                                },
                                "selected_for": ["user_id_2"],
                                "matched_preferences": ["HISTORY_CULTURE"]
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AiPlaceSelectionResponse response = client.selectPlaces(request);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.data().places()).hasSize(2);
        assertThat(response.data().places().getFirst().id())
                .isEqualTo("ChIJT-dJpdYkcDURU4xGNLNrBdY");
        assertThat(response.data().places().getFirst().displayName().text())
                .isEqualTo("경복궁");
        assertThat(response.data().places().getFirst().selectedFor())
                .containsExactly("user_id_1", "user_id_3");
        assertThat(response.data().places().getFirst().matchedPreferences())
                .containsExactly("HISTORY_CULTURE");
        assertThat(response.data().places().getFirst().editorialSummary())
                .isNull();
        assertThat(response.data().places().get(1).editorialSummary().text())
                .isEqualTo("전통 한옥이 밀집된 서울의 대표적인 역사 마을.");
        server.verify();
    }
}
