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

    public String customProfileImageUrl() {
        if (kakaoAccount == null
                || kakaoAccount.profile() == null) {
            return null;
        }

        Profile profile = kakaoAccount.profile();
        if (Boolean.TRUE.equals(profile.defaultImage())
                || profile.profileImageUrl() == null
                || profile.profileImageUrl().isBlank()) {
            return null;
        }

        return profile.profileImageUrl();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KakaoAccount(Profile profile) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(
            String nickname,
            @JsonProperty("profile_image_url") String profileImageUrl,
            @JsonProperty("is_default_image") Boolean defaultImage
    ) {
    }
}
