package com.planit.auth.token;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    private final TokenHasher tokenHasher =
            new TokenHasher();

    @DisplayName("토큰을 SHA-256으로 해싱한다")
    @Test
    void hashesTokenWithSha256() {
        String tokenHash = tokenHasher.sha256(
                "test-refresh-token"
        );

        assertThat(tokenHash).isEqualTo(
                "0a9b110d5e553bd98e9965c70a601c15"
                        + "c36805016ba60d54f20f5830c39edcde"
        );
    }

    @DisplayName("64자리 소문자 16진수 해시를 생성한다")
    @Test
    void createsLowercaseHexHashWith64Characters() {
        String tokenHash = tokenHasher.sha256(
                "another-test-token"
        );

        assertThat(tokenHash)
                .matches("[0-9a-f]{64}");
    }
}
