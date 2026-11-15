package com.planit.mq.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * AI 작업 메시지 큐 설정.
 *
 * @param confirmTimeout 발행 후 publisher confirm을 기다리는 최대 시간. 지나면 confirm 유실로 본다.
 * @param outbox         Outbox 발행 재시도 설정
 */
@Validated
@ConfigurationProperties(prefix = "planit.mq")
public record RabbitMqProperties(
        @NotNull Duration confirmTimeout,
        @Valid @NotNull Outbox outbox
) {

    /**
     * 저장 직후 Relay가 처음 재발행할 수 있을 때까지의 시간.
     * 커밋 직후 발행한 메시지의 confirm을 기다리는 동안 Relay가 같은 메시지를 다시 발행하지 않게 한다.
     */
    public Duration firstRelayDelay() {
        return confirmTimeout.plus(outbox.initialGrace());
    }

    /**
     * @param initialGrace      최초 재발행 시각에 confirm 대기시간 외에 더 두는 여유
     * @param backoffBase       재시도 시작 간격. 재시도할 때마다 두 배가 된다
     * @param nackMaxRetries    NACK 재시도 한도. 넘으면 실패 처리한다 (5s, 10s, 20s)
     * @param failureMaxRetries confirm 유실·연결 예외 재시도 한도. 넘으면 실패 처리한다 (5s, 10s, 20s, 40s)
     * @param relay             Relay 설정
     * @param failedRetention   발행에 실패한 Outbox 행을 지우지 않고 남겨 두는 기간
     * @param cleanup           Outbox 정리 설정
     */
    public record Outbox(
            @NotNull Duration initialGrace,
            @NotNull Duration backoffBase,
            @Min(0) @Max(Byte.MAX_VALUE) int nackMaxRetries,
            @Min(0) @Max(Byte.MAX_VALUE) int failureMaxRetries,
            @Valid @NotNull Relay relay,
            @NotNull Duration failedRetention,
            @Valid @NotNull Cleanup cleanup
    ) {
    }

    /** @param batchSize Relay가 한 번에 조회해 발행하는 최대 행 수 */
    public record Relay(@Min(1) int batchSize) {
    }

    /** @param deleteBatchSize 정리할 때 DELETE 한 번에 지우는 최대 행 수 */
    public record Cleanup(@Min(1) int deleteBatchSize) {
    }
}
