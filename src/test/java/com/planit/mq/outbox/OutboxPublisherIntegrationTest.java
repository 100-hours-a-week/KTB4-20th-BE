package com.planit.mq.outbox;

import com.planit.mq.job.AiJobType;
import com.planit.mq.topology.AiExchange;
import com.planit.mq.topology.AiJobQueue;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OutboxPublisherIntegrationTest {

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
    private OutboxPublisher publisher;

    @Autowired
    private MessageOutboxRepository repository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final List<Long> savedIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        repository.deleteAllById(savedIds);
        rabbitTemplate.execute(channel -> channel.queuePurge(AiJobQueue.SCHEDULE_GENERATE.taskQueue()));
    }

    @DisplayName("커밋 직후 발행하면 Task Queue에 계약대로 메시지가 들어가고 Outbox는 발행 완료가 된다")
    @Test
    void publishesToTaskQueue() throws Exception {
        MessageOutbox outbox = save(AiJobQueue.SCHEDULE_GENERATE.routingKey());

        publisher.publishAsync(outbox.getId());

        MessageOutbox published = awaitOutbox(outbox, found -> found.getStatus() != MessageOutboxStatus.PENDING);
        assertThat(published.getStatus()).isEqualTo(MessageOutboxStatus.PUBLISHED);
        assertThat(published.getPublishedAt()).isNotNull();

        Message message = rabbitTemplate.receive(AiJobQueue.SCHEDULE_GENERATE.taskQueue(), 5_000);
        assertThat(message).isNotNull();
        MessageProperties properties = message.getMessageProperties();
        assertThat(properties.getContentType()).isEqualTo("application/json");
        assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getMessageId()).isEqualTo(outbox.getMessageId().toString());
        assertThat(properties.getCorrelationId()).isEqualTo("42");
        assertThat(properties.getType()).isEqualTo("SCHEDULE_GENERATE");
        assertThat(properties.getAppId()).isEqualTo("planit-backend");
        assertThat(properties.getTimestamp()).isNotNull();
        assertThat((Integer) properties.getHeader(OutboxPublisher.RETRY_COUNT_HEADER)).isZero();
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).contains("\"jobId\"");
    }

    @DisplayName("보낼 큐가 없는 routing key면 return을 받아 재시도 없이 실패 처리한다")
    @Test
    void failsWhenMessageIsReturned() throws Exception {
        MessageOutbox outbox = save("unknown-routing-key");

        publisher.publishAsync(outbox.getId());

        MessageOutbox failed = awaitOutbox(outbox, found -> found.getStatus() != MessageOutboxStatus.PENDING);
        assertThat(failed.getStatus()).isEqualTo(MessageOutboxStatus.FAILED);
        assertThat(failed.getLastError()).startsWith("RETURNED: 312");
    }

    private MessageOutbox save(String routingKey) {
        LocalDateTime now = LocalDateTime.now();
        MessageOutbox saved = repository.save(MessageOutbox.pending(
                AiJobType.SCHEDULE_GENERATE,
                42L,
                AiExchange.REQUEST,
                routingKey,
                "{\"schemaVersion\": 1, \"jobId\": \"42\"}",
                now,
                now.plusSeconds(10)
        ));
        savedIds.add(saved.getId());
        return saved;
    }

    private MessageOutbox awaitOutbox(MessageOutbox outbox, Predicate<MessageOutbox> condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        MessageOutbox found = repository.findById(outbox.getId()).orElseThrow();
        while (!condition.test(found) && System.nanoTime() < deadline) {
            Thread.sleep(100);
            found = repository.findById(outbox.getId()).orElseThrow();
        }
        return found;
    }
}
