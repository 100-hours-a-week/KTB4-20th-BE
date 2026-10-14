package com.planit.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AiWeatherPrecheckClient {

    private static final String INTERNAL_TOKEN_HEADER =
            "X-Internal-Token";

    private final RestClient restClient;
    private final AiProperties aiProperties;

    public AiWeatherPrecheckClient(
            @Qualifier("aiRestClient") RestClient restClient,
            AiProperties aiProperties
    ) {
        this.restClient = restClient;
        this.aiProperties = aiProperties;
    }

    public AiWeatherPrecheckResponse precheck() {
        AiWeatherPrecheckResponse response = restClient.get()
                .uri(aiProperties.baseUrl().resolve("/precheck"))
                .header(
                        INTERNAL_TOKEN_HEADER,
                        aiProperties.internalToken()
                )
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(AiWeatherPrecheckResponse.class);

        if (response == null) {
            throw new RestClientException(
                    "AI 날씨 사전 점검 응답이 없습니다."
            );
        }
        if (response.statusCode() != 200) {
            throw new RestClientException(
                    "AI 날씨 사전 점검 응답 상태가 올바르지 않습니다."
            );
        }
        if (response.data() == null
                || response.data().affectedPlaceIds() == null
                || response.data().reasons() == null) {
            throw new RestClientException(
                    "AI 날씨 사전 점검 응답 형식이 올바르지 않습니다."
            );
        }

        return response;
    }
}
