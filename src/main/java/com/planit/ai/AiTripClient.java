package com.planit.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AiTripClient {

    private static final String INTERNAL_TOKEN_HEADER =
            "X-Internal-Token";

    private final RestClient restClient;
    private final AiProperties aiProperties;

    public AiTripClient(
            @Qualifier("aiRestClient") RestClient restClient,
            AiProperties aiProperties
    ) {
        this.restClient = restClient;
        this.aiProperties = aiProperties;
    }

    public AiPlaceSelectionResponse selectPlaces(
            AiPlaceSelectionRequest request
    ) {
        AiPlaceSelectionResponse response = restClient.post()
                .uri(aiProperties.baseUrl().resolve(
                        "/trips/place-selection"
                ))
                .header(
                        INTERNAL_TOKEN_HEADER,
                        aiProperties.internalToken()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AiPlaceSelectionResponse.class);

        if (response == null) {
            throw new RestClientException(
                    "AI 장소 선정 응답이 없습니다."
            );
        }

        return response;
    }
}
