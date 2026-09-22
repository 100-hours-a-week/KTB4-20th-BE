package com.planit.auth.token;

import com.planit.auth.config.AuthProperties;
import com.planit.auth.dto.AccessTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtEncoder jwtEncoder;
    private final AuthProperties authProperties;

    public AccessTokenResponse issue(UUID userPublicId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(
                authProperties.jwt().accessTokenTtl()
        );

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("at+jwt")
                .build();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(authProperties.jwt().issuer())
                .audience(List.of(
                        authProperties.jwt().audience()
                ))
                .subject(userPublicId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();

        String accessToken = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();

        return new AccessTokenResponse(
                accessToken,
                "Bearer",
                authProperties.jwt()
                        .accessTokenTtl()
                        .toSeconds()
        );
    }
}
