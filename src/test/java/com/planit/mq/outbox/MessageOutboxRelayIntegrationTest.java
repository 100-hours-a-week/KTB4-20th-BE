package com.planit.mq.outbox;

import com.planit.mq.job.AiJobType;
import com.planit.mq.topology.AiExchange;
import com.planit.mq.topology.AiJobQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 백그라운드 Relay 스케줄러가 테스트 데이터를 먼저 가져가지 않도록,
 * 다음 발행 시각을 하루 뒤로 두고 그 시각을 기준으로 직접 Relay를 실행한다.
 */
@SpringBootTest
@Testcontainers
class MessageOutboxRelayIntegrationTest {

    private static final int BATCH_SIZE = 2;

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
        registry.add("planit.mq.outbox.relay.batch-size", () -> BATCH_SIZE);
    }

    @Autowired
    private MessageOutboxRelay relay;

    @Autowired
    private MessageOutboxRepository repository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    private final LocalDateTime createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    private final LocalDateTime dueAt = createdAt.plusDays(1);
    private final List<Long> savedIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        repository.deleteAllById(savedIds);
        rabbitTemplate.execute(channel -> channel.queuePurge(AiJobQueue.SCHEDULE_GENERATE.taskQueue()));
    }

    @DisplayName("발행 시각이 된 메시지를 batch-size만큼씩 발행하고, confirm을 반영한 뒤 돌아온다")
    @Test
    void relaysDueMessagesInBatches() {
        MessageOutbox first = save(AiJobQueue.SCHEDULE_GENERATE.routingKey(), dueAt);
        MessageOutbox second = save(AiJobQueue.SCHEDULE_GENERATE.routingKey(), dueAt);
        MessageOutbox third = save(AiJobQueue.SCHEDULE_GENERATE.routingKey(), dueAt);

        assertThat(relay.relayDue(dueAt)).isEqualTo(BATCH_SIZE);
        assertThat(statusOf(first)).isEqualTo(MessageOutboxStatus.PUBLISHED);
        assertThat(statusOf(second)).isEqualTo(MessageOutboxStatus.PUBLISHED);
        assertThat(statusOf(third)).isEqualTo(MessageOutboxStatus.PENDING);

        assertThat(relay.relayDue(dueAt)).isOne();
        assertThat(statusOf(third)).isEqualTo(MessageOutboxStatus.PUBLISHED);
        assertThat(relay.relayDue(dueAt)).isZero();

        List<String> messageIds = new ArrayList<>();
        Message message;
        while ((message = rabbitTemplate.receive(AiJobQueue.SCHEDULE_GENERATE.taskQueue(), 1_000)) != null) {
            messageIds.add(message.getMessageProperties().getMessageId());
        }
        assertThat(messageIds).containsExactlyInAnyOrder(
                first.getMessageId().toString(),
                second.getMessageId().toString(),
                third.getMessageId().toString()
        );
    }

    @DisplayName("발행 시각이 아직 안 된 메시지는 발행하지 않는다")
    @Test
    void skipsMessagesNotYetDue() {
        MessageOutbox notYet = save(AiJobQueue.SCHEDULE_GENERATE.routingKey(), dueAt.plusSeconds(1));

        assertThat(relay.relayDue(dueAt)).isZero();
        assertThat(statusOf(notYet)).isEqualTo(MessageOutboxStatus.PENDING);
    }

    @DisplayName("Relay가 발행한 메시지도 return을 받으면 재시도 없이 실패 처리한다")
    @Test
    void failsReturnedMessage() {
        MessageOutbox unroutable = save("unknown-routing-key", dueAt);

        assertThat(relay.relayDue(dueAt)).isOne();

        MessageOutbox found = repository.findById(unroutable.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(MessageOutboxStatus.FAILED);
        assertThat(found.getLastError()).startsWith("RETURNED: 312");
    }

    private MessageOutbox save(String routingKey, LocalDateTime nextAttemptAt) {
        MessageOutbox saved = repository.save(MessageOutbox.pending(
                AiJobType.SCHEDULE_GENERATE,
                42L,
                AiExchange.REQUEST,
                routingKey,
                "{\"schemaVersion\": 1, \"jobId\": \"42\"}",
                createdAt,
                nextAttemptAt
        ));
        savedIds.add(saved.getId());
        return saved;
    }

    private MessageOutboxStatus statusOf(MessageOutbox outbox) {
        return repository.findById(outbox.getId()).orElseThrow().getStatus();
    }
}
