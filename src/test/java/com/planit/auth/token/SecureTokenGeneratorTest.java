package com.planit.auth.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureTokenGeneratorTest {

    private final SecureTokenGenerator tokenGenerator =
            new SecureTokenGenerator();

    @DisplayName("URL에 안전한 토큰을 생성한다")
    @Test
    void generatesUrlSafeToken() {
        String token = tokenGenerator.generate();

        assertThat(token)
                .matches("[A-Za-z0-9_-]{43}");
    }

    @DisplayName("호출할 때마다 서로 다른 토큰을 생성한다")
    @Test
    void generatesDifferentTokenEachTime() {
        String firstToken = tokenGenerator.generate();
        String secondToken = tokenGenerator.generate();

        assertThat(firstToken)
                .isNotEqualTo(secondToken);
    }
}
