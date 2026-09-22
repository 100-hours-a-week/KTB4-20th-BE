package com.planit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final String TOKEN_HASH =
            "e3f0d29ab9f10fc9862b46fcb152876d429aafc96a14358c0802a45d9d1314e5";

    @Test
    void recordsRevokedAt() {
        RefreshToken refreshToken = createRefreshToken();
        LocalDateTime revokedAt = LocalDateTime.of(2026, 9, 19, 12, 0);

        refreshToken.revoke(revokedAt);

        assertThat(refreshToken.getRevokedAt()).isEqualTo(revokedAt);
    }

    @Test
    void keepsFirstRevokedAtWhenRevokedAgain() {
        RefreshToken refreshToken = createRefreshToken();
        LocalDateTime firstRevokedAt = LocalDateTime.of(2026, 9, 19, 12, 0);
        LocalDateTime secondRevokedAt = firstRevokedAt.plusHours(1);

        refreshToken.revoke(firstRevokedAt);
        refreshToken.revoke(secondRevokedAt);

        assertThat(refreshToken.getRevokedAt()).isEqualTo(firstRevokedAt);
    }

    private RefreshToken createRefreshToken() {
        User user = new User(
                new ImageFile(),
                UUID.fromString("01991f6e-7300-7b21-a3cc-1436db3df95e"),
                "플랜잇사용자"
        );
        LocalDateTime issuedAt = LocalDateTime.of(2026, 9, 19, 12, 0);

        return new RefreshToken(
                user,
                TOKEN_HASH,
                issuedAt,
                issuedAt.plusDays(30)
        );
    }
}
