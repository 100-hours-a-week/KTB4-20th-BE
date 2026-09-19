package com.planit.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "planit.auth")
public record AuthProperties(
        @NotNull URI frontendBaseUrl,
        @NotNull URI allowedFrontendOrigin,
        boolean cookieSecure,
        @NotBlank String oauthStateCookieName,
        @NotNull Duration oauthStateCookieMaxAge,
        @Valid @NotNull Kakao kakao
) {

    public record Kakao(
            @NotBlank String clientId,
            @NotBlank String clientSecret,
            @NotNull URI redirectUri,
            @NotNull URI authorizationUri,
            @NotNull URI tokenUri,
            @NotNull URI userInfoUri
    ) {
    }
}