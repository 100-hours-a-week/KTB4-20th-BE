package com.planit.mq.monitor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MessageQueueMonitorPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "planit.mq.monitor.management-base-url=http://localhost:15672",
                    "planit.mq.monitor.virtual-host=planit",
                    "planit.mq.monitor.username=planit-monitor",
                    "planit.mq.monitor.password=secret",
                    "planit.mq.monitor.connect-timeout=300ms",
                    "planit.mq.monitor.read-timeout=500ms",
                    "planit.mq.monitor.task-block-ratio=0.95",
                    "planit.mq.monitor.task-unblock-ratio=0.50",
                    "planit.mq.monitor.dlq-block-ratio=0.90",
                    "planit.mq.monitor.dlq-unblock-ratio=0.50"
            );

    @DisplayName("Management API 접속 정보와 차단·해제 비율을 읽는다")
    @Test
    void bindsMonitorProperties() {
        contextRunner.run(context -> {
            MessageQueueMonitorProperties properties = context.getBean(MessageQueueMonitorProperties.class);

            assertThat(properties.managementBaseUrl()).isEqualTo(URI.create("http://localhost:15672"));
            assertThat(properties.virtualHost()).isEqualTo("planit");
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(300));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofMillis(500));
            assertThat(properties.taskBlockRatio()).isEqualTo(0.95);
            assertThat(properties.dlqUnblockRatio()).isEqualTo(0.50);
        });
    }

    @DisplayName("비율이 0~1 밖이면 기동하지 않는다")
    @Test
    void rejectsRatioOutOfRange() {
        contextRunner
                .withPropertyValues("planit.mq.monitor.task-block-ratio=1.5")
                .run(context -> assertThat(context).hasFailed());
    }

    @DisplayName("해제 비율이 차단 비율보다 낮지 않으면 기동하지 않는다")
    @Test
    void rejectsUnblockRatioNotBelowBlockRatio() {
        contextRunner
                .withPropertyValues("planit.mq.monitor.task-unblock-ratio=0.95")
                .run(context -> assertThat(context).hasFailed());
        contextRunner
                .withPropertyValues("planit.mq.monitor.dlq-unblock-ratio=0.95")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(MessageQueueMonitorProperties.class)
    static class TestConfiguration {
    }
}
