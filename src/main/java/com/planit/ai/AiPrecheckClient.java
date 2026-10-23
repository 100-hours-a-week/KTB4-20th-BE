package com.planit.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
public class AiPrecheckClient {

    private static final String INTERNAL_TOKEN_HEADER =
            "X-Internal-Token";
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final int FORECAST_DAY_COUNT = 9;

    private final RestClient restClient;
    private final AiProperties aiProperties;

    public AiPrecheckClient(
            @Qualifier("aiRestClient") RestClient restClient,
            AiProperties aiProperties
    ) {
        this.restClient = restClient;
        this.aiProperties = aiProperties;
    }

    public AiPrecheckResponse precheck() {
        AiPrecheckResponse response = restClient.post()
                .uri(aiProperties.baseUrl().resolve("/trips/precheck"))
                .header(
                        INTERNAL_TOKEN_HEADER,
                        aiProperties.internalToken()
                )
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(AiPrecheckResponse.class);

        if (response == null) {
            throw new RestClientException(
                    "AI 외부 요인 사전 점검 응답이 없습니다."
            );
        }
        if (response.statusCode() != 200) {
            throw new RestClientException(
                    "AI 외부 요인 사전 점검 응답 상태가 올바르지 않습니다."
            );
        }
        if (!hasValidData(response.data())) {
            throw new RestClientException(
                    "AI 외부 요인 사전 점검 응답 형식이 올바르지 않습니다."
            );
        }

        return response;
    }

    private boolean hasValidData(AiPrecheckResponse.Data data) {
        if (data == null || data.generatedAt() == null
                || data.forecasts() == null
                || data.forecasts().isEmpty()) {
            return false;
        }

        LocalDate generatedDate = data.generatedAt()
                .atZone(SEOUL_ZONE)
                .toLocalDate();
        LocalDate lastForecastDate = generatedDate.plusDays(
                FORECAST_DAY_COUNT
        );
        LocalDate previousDate = generatedDate;
        List<AiPrecheckResponse.Forecast> forecasts =
                data.forecasts();

        for (AiPrecheckResponse.Forecast forecast : forecasts) {
            if (forecast == null
                    || forecast.date() == null
                    || !forecast.date().isAfter(previousDate)
                    || forecast.date().isAfter(lastForecastDate)
                    || forecast.regions() == null
                    || forecast.regions().stream()
                    .anyMatch(this::hasInvalidRegionForecast)) {
                return false;
            }
            previousDate = forecast.date();
        }
        return true;
    }

    private boolean hasInvalidRegionForecast(
            AiPrecheckResponse.RegionForecast forecast
    ) {
        return forecast == null
                || forecast.region() == null
                || forecast.region().isBlank()
                || forecast.reasons() == null;
    }
}
