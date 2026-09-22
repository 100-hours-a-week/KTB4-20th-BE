package com.planit.auth.kakao;

import com.planit.auth.config.AuthProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
@Component
public class KakaoOAuthClient {

    private final RestClient restClient;
    private final AuthProperties authProperties;

    public String exchangeCode(String code) {
        MultiValueMap<String, String> form =
                new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add(
                "client_id",
                authProperties.kakao().clientId()
        );
        form.add(
                "redirect_uri",
                authProperties.kakao().redirectUri().toString()
        );
        form.add("code", code);
        form.add(
                "client_secret",
                authProperties.kakao().clientSecret()
        );

        KakaoTokenResponse response = restClient.post()
                .uri(authProperties.kakao().tokenUri())
                .contentType(
                        MediaType.APPLICATION_FORM_URLENCODED
                )
                .body(form)
                .retrieve()
                .body(KakaoTokenResponse.class);

        if (response == null) {
            throw new org.springframework.web.client.RestClientException(
                    "카카오 토큰 응답이 없습니다."
            );
        }

        return response.accessToken();
    }

    public KakaoUserResponse getUser(
            String kakaoAccessToken
    ) {
        return restClient.get()
                .uri(authProperties.kakao().userInfoUri())
                .headers(headers -> headers.setBearerAuth(
                        kakaoAccessToken
                ))
                .retrieve()
                .body(KakaoUserResponse.class);
    }

    public void unlink(String providerUserId) {
        MultiValueMap<String, String> form =
                new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", providerUserId);

        try {
            restClient.post()
                    .uri(authProperties.kakao().unlinkUri())
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "KakaoAK "
                                    + authProperties.kakao().adminKey()
                    )
                    .contentType(
                            MediaType.APPLICATION_FORM_URLENCODED
                    )
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException exception) {
            if (!exception.getResponseBodyAsString()
                    .matches("(?s).*\\\"code\\\"\\s*:\\s*-101.*")) {
                throw exception;
            }
        }
    }
}
