package com.planit.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AuthPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "planit.auth.frontend-base-url=http://localhost:5173",
                    "planit.auth.allowed-frontend-origin=http://localhost:5173",
                    "planit.auth.cookie-secure=false",
                    "planit.auth.oauth-state-cookie-name=planit_oauth_state",
                    "planit.auth.oauth-state-cookie-max-age=10m",
                    "planit.auth.kakao.client-id=test-client-id",
                    "planit.auth.kakao.client-secret=test-client-secret",
                    "planit.auth.kakao.redirect-uri=http://localhost:8080/api/auth/oauth/callback",
                    "planit.auth.kakao.authorization-uri=https://kauth.kakao.com/oauth/authorize",
                    "planit.auth.kakao.token-uri=https://kauth.kakao.com/oauth/token",
                    "planit.auth.kakao.user-info-uri=https://kapi.kakao.com/v2/user/me"
            );

    @Test
    void bindsAuthProperties() {
        contextRunner.run(context -> {
            AuthProperties properties = context.getBean(AuthProperties.class);

            assertThat(properties.frontendBaseUrl())
                    .isEqualTo(URI.create("http://localhost:5173"));
            assertThat(properties.oauthStateCookieMaxAge())
                    .isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.kakao().clientId()).isEqualTo("test-client-id");
            assertThat(properties.kakao().clientSecret()).isEqualTo("test-client-secret");
            assertThat(properties.kakao().redirectUri())
                    .isEqualTo(URI.create("http://localhost:8080/api/auth/oauth/callback"));
        });
    }

    @EnableConfigurationProperties(AuthProperties.class)
    static class TestConfiguration {
    }
}
