package com.planit.mq.intake;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * AI 작업 접수 설정. 다른 메시지 큐 설정과 같은 `planit.mq` 아래에 둔다.
 *
 * @param retryAfter 큐 차단으로 접수하지 않을 때 503 응답 `Retry-After` 헤더로 알려 주는 대기 시간
 */
@Validated
@ConfigurationProperties(prefix = "planit.mq")
public record AiJobIntakeProperties(
        @NotNull Duration retryAfter
) {
}
