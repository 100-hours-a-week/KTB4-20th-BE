package com.planit.mq.intake;

import com.planit.mq.job.AiJobType;
import com.planit.mq.monitor.MessageQueueMonitorStore;
import com.planit.mq.monitor.QueueBlockType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 큐 감시가 켠 플래그를 접수에서 그대로 읽는지 실제 Redis로 확인한다. */
@Testcontainers
class AiJobIntakeGuardRedisIntegrationTest {

    private static final int REDIS_PORT = 6379;
    private static final Duration RETRY_AFTER = Duration.ofSeconds(60);

    @Container
    private final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:8.10.2-alpine")
    )
            .withExposedPorts(REDIS_PORT)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private MessageQueueMonitorStore monitorStore;
    private AiJobIntakeGuard guard;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(REDIS_PORT))
        );
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        monitorStore = new MessageQueueMonitorStore(redisTemplate);
        guard = new AiJobIntakeGuard(redisTemplate, new AiJobIntakeProperties(RETRY_AFTER));
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @DisplayName("차단 플래그가 없으면 접수한다")
    @Test
    void acceptsWithoutBlockFlag() {
        assertThatCode(() -> guard.requireAvailable(AiJobType.SCHEDULE_GENERATE)).doesNotThrowAnyException();
    }

    @DisplayName("작업 큐나 요청 DLQ 차단 플래그가 켜져 있으면 재시도 대기 시간과 함께 거부한다")
    @Test
    void rejectsWhenBlocked() {
        monitorStore.block(QueueBlockType.TASK_QUEUE, AiJobType.SCHEDULE_GENERATE);
        monitorStore.block(QueueBlockType.DEAD_LETTER_QUEUE, AiJobType.PHOTO_JUDGE);

        assertThatThrownBy(() -> guard.requireAvailable(AiJobType.SCHEDULE_GENERATE))
                .isInstanceOfSatisfying(AiJobTemporarilyUnavailableException.class, exception ->
                        assertThat(exception.getRetryAfter()).isEqualTo(RETRY_AFTER));
        assertThatThrownBy(() -> guard.requireAvailable(AiJobType.PHOTO_JUDGE))
                .isInstanceOf(AiJobTemporarilyUnavailableException.class);
        assertThatCode(() -> guard.requireAvailable(AiJobType.PHOTO_MISSION_GENERATE)).doesNotThrowAnyException();
    }

    @DisplayName("차단 플래그를 끄면 다음 요청부터 바로 접수한다")
    @Test
    void acceptsRightAfterUnblock() {
        monitorStore.block(QueueBlockType.TASK_QUEUE, AiJobType.SCHEDULE_GENERATE);
        monitorStore.unblock(QueueBlockType.TASK_QUEUE, AiJobType.SCHEDULE_GENERATE);

        assertThatCode(() -> guard.requireAvailable(AiJobType.SCHEDULE_GENERATE)).doesNotThrowAnyException();
    }

    @DisplayName("전환 플래그 값이 on일 때만 메시지 큐로 접수하고, 바꾸면 다음 요청부터 바로 반영한다")
    @Test
    void readsAsyncFlagOnEveryCall() {
        String key = "planit:mq:async:SCHEDULE_GENERATE";
        assertThat(guard.isAsync(AiJobType.SCHEDULE_GENERATE)).isFalse();

        redisTemplate.opsForValue().set(key, "on");
        assertThat(guard.isAsync(AiJobType.SCHEDULE_GENERATE)).isTrue();
        assertThat(guard.isAsync(AiJobType.PHOTO_JUDGE)).isFalse();

        redisTemplate.opsForValue().set(key, "off");
        assertThat(guard.isAsync(AiJobType.SCHEDULE_GENERATE)).isFalse();

        redisTemplate.delete(key);
        assertThat(guard.isAsync(AiJobType.SCHEDULE_GENERATE)).isFalse();
    }
}
