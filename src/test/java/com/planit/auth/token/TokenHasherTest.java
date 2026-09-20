package com.planit.auth.token;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    private final TokenHasher tokenHasher =
            new TokenHasher();

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

    @Test
    void createsLowercaseHexHashWith64Characters() {
        String tokenHash = tokenHasher.sha256(
                "another-test-token"
        );

        assertThat(tokenHash)
                .matches("[0-9a-f]{64}");
    }
}
