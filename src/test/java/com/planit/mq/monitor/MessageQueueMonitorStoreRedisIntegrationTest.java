package com.planit.mq.monitor;

import com.planit.mq.job.AiJobType;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MessageQueueMonitorStoreRedisIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    private final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:8.10.2-alpine")
    )
            .withExposedPorts(REDIS_PORT)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private MessageQueueMonitorStore store;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(redis.getHost(), redis.getMappedPort(REDIS_PORT))
        );
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        store = new MessageQueueMonitorStore(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @DisplayName("차단 플래그를 문서의 키로 켜고 끄며, 상태가 바뀐 호출만 true를 돌려준다")
    @Test
    void togglesBlockFlag() {
        assertThat(store.block(QueueBlockType.TASK_QUEUE, AiJobType.SCHEDULE_GENERATE)).isTrue();
        assertThat(store.block(QueueBlockType.TASK_QUEUE, AiJobType.SCHEDULE_GENERATE)).isFalse();
        assertThat(redisTemplate.opsForValue().get("planit:mq:block:taskqueue:SCHEDULE_GENERATE")).isEqualTo("1");

        assertThat(store.unblock(QueueBlockType.TASK_QUEUE, AiJobType.SCHEDULE_GENERATE)).isTrue();
        assertThat(store.unblock(QueueBlockType.TASK_QUEUE, AiJobType.SCHEDULE_GENERATE)).isFalse();
        assertThat(redisTemplate.hasKey("planit:mq:block:taskqueue:SCHEDULE_GENERATE")).isFalse();
    }

    @DisplayName("DLQ 차단 플래그는 작업 큐 플래그와 따로 저장한다")
    @Test
    void storesDeadLetterQueueFlagSeparately() {
        store.block(QueueBlockType.DEAD_LETTER_QUEUE, AiJobType.PHOTO_JUDGE);

        assertThat(redisTemplate.hasKey("planit:mq:block:deadletterqueue:PHOTO_JUDGE")).isTrue();
        assertThat(redisTemplate.hasKey("planit:mq:block:taskqueue:PHOTO_JUDGE")).isFalse();
    }

    @DisplayName("응답 DLQ는 비어 있다가 메시지가 생긴 첫 호출만 true를 돌려준다")
    @Test
    void marksResultDlqOnlyOnChange() {
        assertThat(store.markResultDlqNotEmpty()).isTrue();
        assertThat(store.markResultDlqNotEmpty()).isFalse();

        store.markResultDlqEmpty();

        assertThat(store.markResultDlqNotEmpty()).isTrue();
    }
}
