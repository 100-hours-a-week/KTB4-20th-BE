package com.planit.auth.token;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureTokenGeneratorTest {

    private final SecureTokenGenerator tokenGenerator =
            new SecureTokenGenerator();

    @Test
    void generatesUrlSafeToken() {
        String token = tokenGenerator.generate();

        assertThat(token)
                .matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void generatesDifferentTokenEachTime() {
        String firstToken = tokenGenerator.generate();
        String secondToken = tokenGenerator.generate();

        assertThat(firstToken)
                .isNotEqualTo(secondToken);
    }
}
