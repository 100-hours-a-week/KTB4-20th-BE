package com.planit.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "planit.ai")
public record AiProperties(
        URI baseUrl,
        String internalToken,
        Duration connectTimeout,
        Duration readTimeout
) {
}
