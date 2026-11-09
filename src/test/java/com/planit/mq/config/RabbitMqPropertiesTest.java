package com.planit.mq.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMqPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "planit.mq.confirm-timeout=5s",
                    "planit.mq.outbox.initial-grace=5s",
                    "planit.mq.outbox.backoff-base=5s",
                    "planit.mq.outbox.nack-max-retries=3",
                    "planit.mq.outbox.failure-max-retries=4",
                    "planit.mq.outbox.relay.batch-size=500",
                    "planit.mq.outbox.failed-retention=30d",
                    "planit.mq.outbox.cleanup.delete-batch-size=1000"
            );

    @DisplayName("confirm 대기시간과 Outbox 재시도 설정을 읽고, 최초 재발행 지연은 둘의 합이다")
    @Test
    void bindsOutboxProperties() {
        contextRunner.run(context -> {
            RabbitMqProperties properties = context.getBean(RabbitMqProperties.class);

            assertThat(properties.confirmTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.outbox().backoffBase()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.outbox().nackMaxRetries()).isEqualTo(3);
            assertThat(properties.outbox().failureMaxRetries()).isEqualTo(4);
            assertThat(properties.firstRelayDelay()).isEqualTo(Duration.ofSeconds(10));
        });
    }

    @DisplayName("Relay 조회 건수, 실패 행 보관 기간, 정리 삭제 건수를 읽는다")
    @Test
    void bindsRelayAndCleanupProperties() {
        contextRunner.run(context -> {
            RabbitMqProperties.Outbox outbox = context.getBean(RabbitMqProperties.class).outbox();

            assertThat(outbox.relay().batchSize()).isEqualTo(500);
            assertThat(outbox.failedRetention()).isEqualTo(Duration.ofDays(30));
            assertThat(outbox.cleanup().deleteBatchSize()).isEqualTo(1000);
        });
    }

    @DisplayName("Relay 조회 건수나 정리 삭제 건수가 0이면 기동하지 않는다")
    @Test
    void rejectsZeroBatchSize() {
        contextRunner
                .withPropertyValues("planit.mq.outbox.relay.batch-size=0")
                .run(context -> assertThat(context).hasFailed());
        contextRunner
                .withPropertyValues("planit.mq.outbox.cleanup.delete-batch-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @DisplayName("재시도 한도가 음수면 기동하지 않는다")
    @Test
    void rejectsNegativeRetryLimit() {
        contextRunner
                .withPropertyValues("planit.mq.outbox.nack-max-retries=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(RabbitMqProperties.class)
    static class TestConfiguration {
    }
}
