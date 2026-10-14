package com.planit.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiWeatherPrecheckClientTest {

    private MockRestServiceServer server;
    private AiWeatherPrecheckClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiWeatherPrecheckClient(
                builder.build(),
                new AiProperties(
                        URI.create("http://localhost:8000"),
                        "test-internal-token",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(30)
                )
        );
    }

    @DisplayName("AI 서버에 날씨 사전 점검을 요청한다")
    @Test
    void requestsWeatherPrecheckFromAiServer() {
        server.expect(once(), requestTo(
                        "http://localhost:8000/precheck"
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        "X-Internal-Token",
                        "test-internal-token"
                ))
                .andRespond(withSuccess("""
                        {
                          "status_code": 200,
                          "data": {
                            "has_issue": true,
                            "affected_place_ids": [
                              "place_123",
                              "place_456"
                            ],
                            "reasons": [
                              {
                                "category": "weather_risk",
                                "detail": "heavy_rain"
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AiWeatherPrecheckResponse response = client.precheck();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.data().hasIssue()).isTrue();
        assertThat(response.data().affectedPlaceIds())
                .containsExactly("place_123", "place_456");
        assertThat(response.data().reasons())
                .containsExactly(new AiWeatherPrecheckResponse.Reason(
                        "weather_risk",
                        "heavy_rain"
                ));
        server.verify();
    }

    @DisplayName("이상 상황이 없어도 정상 응답으로 처리한다")
    @Test
    void acceptsPrecheckWithoutWeatherIssue() {
        server.expect(once(), requestTo(
                        "http://localhost:8000/precheck"
                ))
                .andRespond(withSuccess("""
                        {
                          "status_code": 200,
                          "data": {
                            "has_issue": false,
                            "affected_place_ids": [],
                            "reasons": []
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AiWeatherPrecheckResponse response = client.precheck();

        assertThat(response.data().hasIssue()).isFalse();
        assertThat(response.data().affectedPlaceIds()).isEmpty();
        assertThat(response.data().reasons()).isEmpty();
        server.verify();
    }

    @DisplayName("AI 서버 응답 본문이 없으면 실패한다")
    @Test
    void rejectsEmptyPrecheckResponse() {
        server.expect(once(), requestTo(
                        "http://localhost:8000/precheck"
                ))
                .andRespond(withNoContent());

        assertThatThrownBy(client::precheck)
                .isInstanceOf(RestClientException.class)
                .hasMessage("AI 날씨 사전 점검 응답이 없습니다.");
        server.verify();
    }

    @DisplayName("응답 본문의 상태 코드가 200이 아니면 실패한다")
    @Test
    void rejectsInvalidPrecheckStatusCode() {
        server.expect(once(), requestTo(
                        "http://localhost:8000/precheck"
                ))
                .andRespond(withSuccess("""
                        {
                          "status_code": 500,
                          "data": {
                            "has_issue": false,
                            "affected_place_ids": [],
                            "reasons": []
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::precheck)
                .isInstanceOf(RestClientException.class)
                .hasMessage(
                        "AI 날씨 사전 점검 응답 상태가 올바르지 않습니다."
                );
        server.verify();
    }

    @DisplayName("필수 응답 데이터가 없으면 실패한다")
    @Test
    void rejectsIncompletePrecheckResponse() {
        server.expect(once(), requestTo(
                        "http://localhost:8000/precheck"
                ))
                .andRespond(withSuccess("""
                        {
                          "status_code": 200,
                          "data": {
                            "has_issue": true
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::precheck)
                .isInstanceOf(RestClientException.class)
                .hasMessage(
                        "AI 날씨 사전 점검 응답 형식이 올바르지 않습니다."
                );
        server.verify();
    }
}
