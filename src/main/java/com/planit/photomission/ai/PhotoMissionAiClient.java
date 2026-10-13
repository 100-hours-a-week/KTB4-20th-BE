package com.planit.photomission.ai;

import com.planit.ai.AiProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PhotoMissionAiClient {

    private static final String INTERNAL_TOKEN_HEADER =
            "X-Internal-Token";

    private final RestClient restClient;
    private final AiProperties aiProperties;

    public PhotoMissionAiClient(
            @Qualifier("aiRestClient") RestClient restClient,
            AiProperties aiProperties
    ) {
        this.restClient = restClient;
        this.aiProperties = aiProperties;
    }

    public PhotoMissionVerifyResponse verify(
            Long missionId,
            PhotoMissionVerifyRequest request
    ) {
        PhotoMissionVerifyResponse response = restClient.post()
                .uri(aiProperties.baseUrl().resolve(
                        "/photomissions/" + missionId + "/verify"
                ))
                .header(
                        INTERNAL_TOKEN_HEADER,
                        aiProperties.internalToken()
                )
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PhotoMissionVerifyResponse.class);

        if (response == null) {
            throw new RestClientException(
                    "AI 포토미션 판정 응답이 없습니다."
            );
        }

        return response;
    }
}
