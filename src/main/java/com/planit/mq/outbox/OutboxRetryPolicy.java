package com.planit.mq.outbox;

import com.planit.mq.config.RabbitMqProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Random;

/**
 * Outbox 발행 재시도 한도와 다음 시도까지의 간격을 정한다.
 * 간격은 `backoff-base × 2^(n-1)`에 그 절반 이하의 무작위 값(equal jitter)을 더해,
 * 같은 장애로 실패한 메시지들이 같은 시각에 몰려 재발행되지 않게 한다.
 */
@Component
public class OutboxRetryPolicy {

    private final RabbitMqProperties.Outbox properties;
    /** 여러 confirm 처리 스레드가 함께 쓴다. Random은 스레드 안전하다 */
    private final Random random;

    @Autowired
    public OutboxRetryPolicy(RabbitMqProperties properties) {
        this(properties, new Random());
    }

    OutboxRetryPolicy(RabbitMqProperties properties, Random random) {
        this.properties = properties.outbox();
        this.random = random;
    }

    /** @param attempt 이번 NACK을 포함한 누적 횟수 */
    public boolean isNackExhausted(int attempt) {
        return attempt > properties.nackMaxRetries();
    }

    /** @param attempt 이번 confirm 유실·연결 예외를 포함한 누적 횟수 */
    public boolean isFailureExhausted(int attempt) {
        return attempt > properties.failureMaxRetries();
    }

    /** @param attempt 이번 실패를 포함한 누적 횟수(1부터) */
    public Duration backoff(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("재시도 횟수는 1 이상이어야 합니다");
        }
        long baseMillis = properties.backoffBase().toMillis() << (attempt - 1);
        long jitterMillis = random.nextLong(baseMillis / 2 + 1);
        return Duration.ofMillis(baseMillis + jitterMillis);
    }
}
