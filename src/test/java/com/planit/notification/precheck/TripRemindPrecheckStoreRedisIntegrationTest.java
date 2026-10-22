package com.planit.notification.precheck;

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
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class TripRemindPrecheckStoreRedisIntegrationTest {

    private static final int REDIS_PORT = 6379;

    @Container
    private final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:8.10.2-alpine")
    )
            .withExposedPorts(REDIS_PORT)
            .waitingFor(Wait.forLogMessage(
                    ".*Ready to accept connections.*\\n",
                    1
            ));

    private LettuceConnectionFactory connectionFactory;
    private TripRemindPrecheckStore store;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(
                        redis.getHost(),
                        redis.getMappedPort(REDIS_PORT)
                )
        );
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        StringRedisTemplate redisTemplate =
                new StringRedisTemplate(connectionFactory);
        store = new TripRemindPrecheckStore(
                redisTemplate,
                JsonMapper.builder().build()
        );
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @DisplayName("실제 Redis에 사전 점검 키가 없으면 빈 결과를 반환한다")
    @Test
    void returnsEmptyForMissingPrecheck() {
        assertThat(store.find(LocalDate.of(2026, 10, 11))).isEmpty();
    }

    @DisplayName("실제 Redis에 저장한 사전 점검을 TTL과 함께 조회한다")
    @Test
    void findsCreatedPrecheck() {
        LocalDate targetDate = LocalDate.of(2026, 10, 11);

        assertThat(store.create(targetDate, cache())).isEqualTo(
                TripRemindPrecheckCreateResult.CREATED
        );

        TripRemindPrecheckSnapshot snapshot = store.find(targetDate)
                .orElseThrow();

        assertThat(snapshot.cache()).isEqualTo(cache());
        assertThat(snapshot.ttlSeconds())
                .isPositive()
                .isLessThanOrEqualTo(TripRemindPrecheckStore.TTL_SECONDS);
    }

    private static TripRemindPrecheckCache cache() {
        return new TripRemindPrecheckCache(
                Instant.parse("2026-10-10T01:00:03Z"),
                TripRemindNotificationWorkflowStatus.PRECHECKED,
                List.of(new TripRemindPrecheckCache.Forecast(
                        LocalDate.of(2026, 10, 11),
                        List.of(new TripRemindPrecheckCache.RegionForecast(
                                "REGION-SEOUL",
                                true,
                                List.of(new TripRemindPrecheckCache.Reason(
                                        "weather_risk",
                                        "heavy_rain"
                                ))
                        ))
                ))
        );
    }
}
