package com.planit.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
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

class AiPrecheckClientTest {

    private MockRestServiceServer server;
    private AiPrecheckClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AiPrecheckClient(
                builder.build(),
                new AiProperties(
                        URI.create("http://localhost:8000"),
                        "test-internal-token",
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(30)
                )
        );
    }

    @DisplayName("AI 서버에서 다음 날부터 9일간의 지역별 외부 요인을 조회한다")
    @Test
    void requestsNineDayRegionalPrecheck() {
        server.expect(once(), requestTo(
                        "http://localhost:8000/precheck"
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(
                        "X-Internal-Token",
                        "test-internal-token"
                ))
                .andRespond(successfulResponse(nineDayForecasts()));

        AiPrecheckResponse response = client.precheck();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.data().generatedAt().toString())
                .isEqualTo("2026-10-10T01:00:03Z");
        assertThat(response.data().forecasts()).hasSize(9);
        assertThat(response.data().forecasts().getFirst().date().toString())
                .isEqualTo("2026-10-11");
        assertThat(response.data().forecasts().getFirst().regions()
                .getFirst().region()).isEqualTo("REGION-SEOUL");
        assertThat(response.data().forecasts().getFirst().regions()
                .getFirst().hasIssue()).isTrue();
        assertThat(response.data().forecasts().getFirst().regions()
                .getFirst().reasons())
                .containsExactly(new AiPrecheckResponse.Reason(
                        "weather_risk",
                        "heavy_rain"
                ));
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
                .hasMessage("AI 외부 요인 사전 점검 응답이 없습니다.");
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
                          "data": null
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(client::precheck)
                .isInstanceOf(RestClientException.class)
                .hasMessage(
                        "AI 외부 요인 사전 점검 응답 상태가 올바르지 않습니다."
                );
        server.verify();
    }

    @DisplayName("예보 날짜 청크가 9개가 아니면 실패한다")
    @Test
    void rejectsForecastsWithInvalidDayCount() {
        server.expect(once(), requestTo(
                        "http://localhost:8000/precheck"
                ))
                .andRespond(successfulResponse("""
                        [
                          {
                            "date": "2026-10-11",
                            "regions": []
                          }
                        ]
                        """));

        assertThatThrownBy(client::precheck)
                .isInstanceOf(RestClientException.class)
                .hasMessage(
                        "AI 외부 요인 사전 점검 응답 형식이 올바르지 않습니다."
                );
        server.verify();
    }

    @DisplayName("예보 날짜가 생성일 다음 날부터 연속되지 않으면 실패한다")
    @Test
    void rejectsNonConsecutiveForecastDates() {
        server.expect(once(), requestTo(
                        "http://localhost:8000/precheck"
                ))
                .andRespond(successfulResponse(
                        nineDayForecasts().replaceFirst(
                                "2026-10-15",
                                "2026-10-16"
                        )
                ));

        assertThatThrownBy(client::precheck)
                .isInstanceOf(RestClientException.class)
                .hasMessage(
                        "AI 외부 요인 사전 점검 응답 형식이 올바르지 않습니다."
                );
        server.verify();
    }

    @DisplayName("지역별 사유 배열이 없으면 실패한다")
    @Test
    void rejectsRegionForecastWithoutReasons() {
        server.expect(once(), requestTo(
                        "http://localhost:8000/precheck"
                ))
                .andRespond(successfulResponse(
                        nineDayForecasts().replace(
                                "\"reasons\": [{",
                                "\"reasons\": null, "
                                        + "\"discarded_reasons\": [{"
                        )
                ));

        assertThatThrownBy(client::precheck)
                .isInstanceOf(RestClientException.class)
                .hasMessage(
                        "AI 외부 요인 사전 점검 응답 형식이 올바르지 않습니다."
                );
        server.verify();
    }

    private static ResponseCreator successfulResponse(String forecasts) {
        return withSuccess("""
                {
                  "status_code": 200,
                  "data": {
                    "generated_at": "2026-10-10T01:00:03Z",
                    "forecasts": %s
                  }
                }
                """.formatted(forecasts), MediaType.APPLICATION_JSON);
    }

    private static String nineDayForecasts() {
        return """
                [
                  {
                    "date": "2026-10-11",
                    "regions": [{
                      "region": "REGION-SEOUL",
                      "has_issue": true,
                      "reasons": [{
                        "category": "weather_risk",
                        "detail": "heavy_rain"
                      }]
                    }]
                  },
                  {"date": "2026-10-12", "regions": []},
                  {"date": "2026-10-13", "regions": []},
                  {"date": "2026-10-14", "regions": []},
                  {"date": "2026-10-15", "regions": []},
                  {"date": "2026-10-16", "regions": []},
                  {"date": "2026-10-17", "regions": []},
                  {"date": "2026-10-18", "regions": []},
                  {"date": "2026-10-19", "regions": []}
                ]
                """;
    }
}
