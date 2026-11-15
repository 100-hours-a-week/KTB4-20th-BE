package com.planit.mq.result;

import com.planit.mq.topology.AiResultQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class AiResultRepublisherIntegrationTest {

    private static final String BODY = "{\"schemaVersion\":1,\"jobId\":\"42\"}";

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
    private AiResultRepublisher republisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @AfterEach
    void tearDown() {
        rabbitTemplate.execute(channel -> channel.queuePurge(AiResultQueue.retryQueue(3)));
        rabbitTemplate.execute(channel -> channel.queuePurge(AiResultQueue.DLQ));
    }

    @DisplayName("재시도 횟수가 남아 있으면 다음 단계 Result Retry Queue로 원본 속성을 그대로 보내고 횟수를 올린다")
    @Test
    void sendsToNextRetryQueue() {
        // 3단계(4초) 큐로 보내 TTL이 지나기 전에 꺼내 본다
        Message original = original(Map.of(
                AiResultRepublisher.RESULT_RETRY_COUNT_HEADER, 2,
                "x-retry-count", 1,
                "x-death", List.of(Map.of("reason", "expired"))
        ));

        assertThat(republisher.retryOrDeadLetter(original, new IllegalStateException("db down"))).isTrue();

        Message retried = rabbitTemplate.receive(AiResultQueue.retryQueue(3), 3_000);
        assertThat(retried).isNotNull();
        MessageProperties properties = retried.getMessageProperties();
        assertThat((Integer) properties.getHeader(AiResultRepublisher.RESULT_RETRY_COUNT_HEADER)).isEqualTo(3);
        assertThat((Integer) properties.getHeader("x-retry-count")).isEqualTo(1);
        assertThat(properties.getHeaders()).doesNotContainKey("x-death");
        assertOriginalPropertiesKept(retried);
    }

    @DisplayName("재시도를 모두 썼으면 실패 원인 헤더를 붙여 응답 DLQ로 보낸다")
    @Test
    void sendsToDlqAfterLastRetry() {
        Message original = original(Map.of(AiResultRepublisher.RESULT_RETRY_COUNT_HEADER, 3));

        assertThat(republisher.retryOrDeadLetter(original, new IllegalStateException("db down"))).isTrue();

        Message dead = rabbitTemplate.receive(AiResultQueue.DLQ, 3_000);
        assertThat(dead).isNotNull();
        MessageProperties properties = dead.getMessageProperties();
        assertThat((String) properties.getHeader(AiResultRepublisher.ERROR_KIND_HEADER))
                .isEqualTo("RESULT_PROCESSING_FAILED");
        assertThat((String) properties.getHeader(AiResultRepublisher.ERROR_MESSAGE_HEADER))
                .isEqualTo("IllegalStateException: db down");
        assertThat(OffsetDateTime.parse(properties.getHeader(AiResultRepublisher.FAILED_AT_HEADER)).getOffset())
                .hasToString("+09:00");
        assertOriginalPropertiesKept(dead);
    }

    @DisplayName("다시 처리해도 같은 메시지는 실패 원인과 함께 바로 응답 DLQ로 보내고, 긴 오류 내용은 500자로 자른다")
    @Test
    void sendsToDlqWithErrorKind() {
        assertThat(republisher.deadLetter(original(Map.of()), AiResultErrorKind.UNSUPPORTED_JOB_TYPE, "x".repeat(600)))
                .isTrue();

        Message dead = rabbitTemplate.receive(AiResultQueue.DLQ, 3_000);
        assertThat(dead).isNotNull();
        assertThat((String) dead.getMessageProperties().getHeader(AiResultRepublisher.ERROR_KIND_HEADER))
                .isEqualTo("UNSUPPORTED_JOB_TYPE");
        assertThat((String) dead.getMessageProperties().getHeader(AiResultRepublisher.ERROR_MESSAGE_HEADER))
                .hasSize(500);
    }

    private static Message original(Map<String, Object> headers) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId("result-message-1");
        properties.setCorrelationId("42");
        properties.setType("SCHEDULE_GENERATE_RESULT");
        properties.setAppId("planit-ai");
        headers.forEach(properties::setHeader);
        return new Message(BODY.getBytes(StandardCharsets.UTF_8), properties);
    }

    private static void assertOriginalPropertiesKept(Message message) {
        MessageProperties properties = message.getMessageProperties();
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(BODY);
        assertThat(properties.getMessageId()).isEqualTo("result-message-1");
        assertThat(properties.getCorrelationId()).isEqualTo("42");
        assertThat(properties.getType()).isEqualTo("SCHEDULE_GENERATE_RESULT");
        assertThat(properties.getAppId()).isEqualTo("planit-ai");
        assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    }
}
