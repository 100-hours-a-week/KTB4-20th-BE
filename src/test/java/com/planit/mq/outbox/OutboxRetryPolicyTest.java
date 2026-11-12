package com.planit.mq.outbox;

import com.planit.mq.config.RabbitMqProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxRetryPolicyTest {

    private static final RabbitMqProperties PROPERTIES = new RabbitMqProperties(
            Duration.ofSeconds(5),
            new RabbitMqProperties.Outbox(Duration.ofSeconds(5), Duration.ofSeconds(5), 3, 4)
    );

    @DisplayName("jitter가 0이면 간격은 5s부터 두 배씩 늘어난다")
    @ParameterizedTest
    @CsvSource({"1, 5000", "2, 10000", "3, 20000", "4, 40000"})
    void doublesBackoffWithoutJitter(int attempt, long expectedMillis) {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(PROPERTIES, fixedRandom(Bound.MIN));

        assertThat(policy.backoff(attempt)).isEqualTo(Duration.ofMillis(expectedMillis));
    }

    @DisplayName("jitter는 기본 간격의 절반까지 더해진다")
    @ParameterizedTest
    @CsvSource({"1, 7500", "4, 60000"})
    void addsAtMostHalfOfBackoffAsJitter(int attempt, long expectedMillis) {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(PROPERTIES, fixedRandom(Bound.MAX));

        assertThat(policy.backoff(attempt)).isEqualTo(Duration.ofMillis(expectedMillis));
    }

    @DisplayName("NACK은 3회까지 재시도하고 4번째에서 한도를 넘는다")
    @Test
    void exhaustsNackOnFourthAttempt() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(PROPERTIES);

        assertThat(policy.isNackExhausted(3)).isFalse();
        assertThat(policy.isNackExhausted(4)).isTrue();
    }

    @DisplayName("confirm 유실은 4회까지 재시도하고 5번째에서 한도를 넘는다")
    @Test
    void exhaustsFailureOnFifthAttempt() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(PROPERTIES);

        assertThat(policy.isFailureExhausted(4)).isFalse();
        assertThat(policy.isFailureExhausted(5)).isTrue();
    }

    @DisplayName("재시도 횟수가 1보다 작으면 간격을 계산하지 않는다")
    @Test
    void rejectsAttemptBelowOne() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(PROPERTIES);

        assertThatThrownBy(() -> policy.backoff(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private enum Bound { MIN, MAX }

    private static Random fixedRandom(Bound bound) {
        return new Random() {
            @Override
            public long nextLong(long upperBound) {
                return bound == Bound.MIN ? 0 : upperBound - 1;
            }
        };
    }
}
