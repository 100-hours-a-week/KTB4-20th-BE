package com.planit.auth.config;

import com.planit.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Configuration
public class JwtConfig {

    private static final int MINIMUM_SECRET_BYTES = 32;

    @Bean
    public SecretKey jwtSecretKey(
            AuthProperties authProperties
    ) {
        byte[] secretBytes;

        try {
            secretBytes = Base64.getDecoder().decode(
                    authProperties.jwt().secretBase64()
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "JWT Secret은 Base64 형식이어야 합니다.",
                    exception
            );
        }

        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT Secret은 최소 32바이트여야 합니다."
            );
        }

        return new SecretKeySpec(
                secretBytes,
                "HmacSHA256"
        );
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey secretKey) {
        return NimbusJwtEncoder
                .withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey secretKey,
            AuthProperties authProperties,
            UserRepository userRepository
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .validateType(false)
                .build();

        OAuth2TokenValidator<Jwt> defaultValidator =
                JwtValidators.createDefaultWithValidators(
                        List.of(
                                new JwtTypeValidator("at+jwt"),
                                new JwtIssuerValidator(
                                        authProperties.jwt().issuer()
                                )
                        )
                );

        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                jwt.getAudience().contains(
                        authProperties.jwt().audience()
                )
                        ? OAuth2TokenValidatorResult.success()
                        : invalidToken("JWT audience가 올바르지 않습니다.");

        OAuth2TokenValidator<Jwt> requiredClaimsValidator = jwt ->
                jwt.getSubject() != null
                        && !jwt.getSubject().isBlank()
                        && jwt.getIssuedAt() != null
                        ? OAuth2TokenValidatorResult.success()
                        : invalidToken("JWT 필수 claim이 없습니다.");

        OAuth2TokenValidator<Jwt> activeUserValidator = jwt -> {
            try {
                UUID publicId = UUID.fromString(jwt.getSubject());

                if (publicId.version() != 7) {
                    return invalidToken(
                            "JWT subject가 올바르지 않습니다."
                    );
                }

                return userRepository
                        .existsByPublicIdAndDeletedAtIsNull(publicId)
                        ? OAuth2TokenValidatorResult.success()
                        : invalidToken(
                                "user_withdrawn",
                                "탈퇴 처리된 사용자입니다."
                        );
            } catch (IllegalArgumentException | NullPointerException exception) {
                return invalidToken("JWT subject가 올바르지 않습니다.");
            }
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        List.of(
                                defaultValidator,
                                audienceValidator,
                                requiredClaimsValidator,
                                activeUserValidator
                        )
                )
        );

        return decoder;
    }

    private OAuth2TokenValidatorResult invalidToken(
            String description
    ) {
        return invalidToken("invalid_token", description);
    }

    private OAuth2TokenValidatorResult invalidToken(
            String code,
            String description
    ) {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(
                        code,
                        description,
                        null
                )
        );
    }
}
