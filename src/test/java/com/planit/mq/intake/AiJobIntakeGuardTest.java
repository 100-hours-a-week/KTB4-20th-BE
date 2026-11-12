package com.planit.mq.intake;

import com.planit.mq.job.AiJobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiJobIntakeGuardTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final AiJobIntakeGuard guard =
            new AiJobIntakeGuard(redisTemplate, new AiJobIntakeProperties(Duration.ofSeconds(60)));

    @DisplayName("Redis 조회에 실패하면 차단하지 않고, 기존 동기 경로로 처리한다")
    @Test
    void fallsBackWhenRedisFails() {
        RedisConnectionFailureException failure = new RedisConnectionFailureException("redis down");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(anyCollection())).thenThrow(failure);
        when(valueOperations.get(anyString())).thenThrow(failure);

        assertThatCode(() -> guard.requireAvailable(AiJobType.SCHEDULE_GENERATE)).doesNotThrowAnyException();
        assertThat(guard.isAsync(AiJobType.SCHEDULE_GENERATE)).isFalse();
    }
}
