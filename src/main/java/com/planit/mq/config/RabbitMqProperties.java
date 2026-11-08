package com.planit.mq.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * AI 작업 메시지 큐 설정.
 *
 * @param confirmTimeout 발행 후 publisher confirm을 기다리는 최대 시간. 지나면 confirm 유실로 본다.
 */
@Validated
@ConfigurationProperties(prefix = "planit.mq")
public record RabbitMqProperties(
        @NotNull Duration confirmTimeout
) {
}
