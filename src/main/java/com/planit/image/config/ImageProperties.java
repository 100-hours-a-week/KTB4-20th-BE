package com.planit.image.config;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "planit.image")
public record ImageProperties(
        @NotNull URI defaultProfileUrl
) {
}
