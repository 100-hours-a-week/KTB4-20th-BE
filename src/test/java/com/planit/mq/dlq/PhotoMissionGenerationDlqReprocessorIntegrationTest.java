package com.planit.mq.dlq;

import com.planit.mq.config.RabbitMqProperties;
import com.planit.mq.topology.AiExchange;
import com.planit.mq.topology.AiJobQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class PhotoMissionGenerationDlqReprocessorIntegrationTest {

    private static final AiJobQueue QUEUE = AiJobQueue.PHOTO_MISSION_GENERATE;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZonedDateTime FIVE_AM = ZonedDateTime.of(2026, 12, 1, 5, 0, 0, 0, SEOUL);

    @Container
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4.1-management")
    );

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.rabbitmq.virtual-host", () -> "/");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private PhotoMissionGenerationDlqMessages messages;

    @Autowired
    private RabbitMqProperties properties;

    @AfterEach
    void tearDown() {
        rabbitTemplate.execute(channel -> channel.queuePurge(QUEUE.taskQueue()));
        rabbitTemplate.execute(channel -> channel.queuePurge(QUEUE.dlq()));
    }

    @DisplayName("deadlineAt이 남은 DLQ 메시지는 x-retry-count 4로 작업 큐에 다시 보내고, 지났거나 읽을 수 없는 메시지는 버린다")
    @Test
    void reprocessesDlq() {
        sendToDlq("1", body("2026-12-01T06:00:00+09:00"));
        sendToDlq("2", body("2026-12-01T05:00:00+09:00"));
        sendToDlq("3", "not json");
        sendToDlq("4", body("2026-12-01T06:00:00+09:00"));

        reprocessorAt(FIVE_AM).reprocess();

        Message first = rabbitTemplate.receive(QUEUE.taskQueue(), 3_000);
        Message second = rabbitTemplate.receive(QUEUE.taskQueue(), 3_000);
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.getMessageProperties().getCorrelationId()).isEqualTo("1");
        assertThat(second.getMessageProperties().getCorrelationId()).isEqualTo("4");
        assertThat((Integer) first.getMessageProperties().getHeader("x-retry-count")).isEqualTo(4);
        assertThat(first.getMessageProperties().getHeaders()).doesNotContainKey("x-error-kind");
        assertThat(new String(first.getBody(), StandardCharsets.UTF_8)).isEqualTo(body("2026-12-01T06:00:00+09:00"));
        assertThat(rabbitTemplate.receive(QUEUE.taskQueue(), 500)).isNull();
        assertThat(messageCount(QUEUE.dlq())).isZero();
    }

    private PhotoMissionGenerationDlqReprocessor reprocessorAt(ZonedDateTime now) {
        return new PhotoMissionGenerationDlqReprocessor(
                rabbitTemplate, messages, properties, Clock.fixed(now.toInstant(), SEOUL)
        );
    }

    private void sendToDlq(String jobId, String body) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType("application/json");
        messageProperties.setMessageId("request-" + jobId);
        messageProperties.setCorrelationId(jobId);
        messageProperties.setType("PHOTO_MISSION_GENERATE");
        messageProperties.setHeader("x-retry-count", 3);
        messageProperties.setHeader("x-error-kind", "AI_UNAVAILABLE");
        rabbitTemplate.send(AiExchange.DLX, QUEUE.dlqRoutingKey(),
                new Message(body.getBytes(StandardCharsets.UTF_8), messageProperties));
    }

    private long messageCount(String queue) {
        return rabbitTemplate.execute(channel -> channel.queueDeclarePassive(queue).getMessageCount());
    }

    private static String body(String deadlineAt) {
        return "{\"schemaVersion\":1,\"jobType\":\"PHOTO_MISSION_GENERATE\",\"deadlineAt\":\"%s\",\"request\":{}}"
                .formatted(deadlineAt);
    }
}
