package com.planit.auth.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserResponse(
        Long id,
        @JsonProperty("kakao_account") KakaoAccount kakaoAccount
) {

    public String providerUserId() {
        return String.valueOf(id);
    }

    public String nickname() {
        if (kakaoAccount == null
                || kakaoAccount.profile() == null) {
            return null;
        }

        return kakaoAccount.profile().nickname();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(Profile profile) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(String nickname) {
    }
}
