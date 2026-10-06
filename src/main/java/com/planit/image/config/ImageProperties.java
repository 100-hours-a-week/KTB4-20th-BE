package com.planit.image.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "planit.image")
public record ImageProperties(
        @NotNull URI defaultProfileUrl,
        @NotBlank String bucket,
        @NotBlank String region,
        @NotNull Duration readUrlTtl,
        @Positive int maxProfileBytes,
        @NotNull Duration downloadConnectTimeout,
        @NotNull Duration downloadReadTimeout
) {
}
