package com.planit.photomission.messaging;

import com.planit.mq.job.AiJobType;
import com.planit.mq.outbox.OutboxPublisher;
import com.planit.mq.topology.AiExchange;
import com.planit.mq.topology.AiJobQueue;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@Testcontainers
class PhotoEvaluationRequestRedeliveryIntegrationTest {

    private static final AiJobQueue JOB_QUEUE = AiJobQueue.PHOTO_JUDGE;

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

    @BeforeEach
    void purgeTaskQueue() {
        rabbitTemplate.execute(channel -> channel.queuePurge(
                JOB_QUEUE.taskQueue()
        ));
    }

    @DisplayName("AI Consumer가 처리 중 종료되면 미ACK 포토미션 판정 요청을 다른 Consumer가 다시 받는다")
    @Test
    void redeliversUnackedPhotoEvaluationRequest() throws Exception {
        Message request = requestMessage();
        rabbitTemplate.send(
                AiExchange.REQUEST,
                JOB_QUEUE.routingKey(),
                request
        );

        Connection firstConnection = connectionFactory().newConnection();
        Channel firstConsumer = firstConnection.createChannel();
        GetResponse firstDelivery = awaitDelivery(firstConsumer);

        assertThat(firstDelivery).isNotNull();
        assertThat(firstDelivery.getEnvelope().isRedeliver()).isFalse();
        assertThat(firstDelivery.getProps().getMessageId())
                .isEqualTo(request.getMessageProperties().getMessageId());

        firstConnection.abort();

        try (Connection secondConnection = connectionFactory().newConnection();
             Channel secondConsumer = secondConnection.createChannel()) {
            GetResponse redelivery = awaitDelivery(secondConsumer);

            assertThat(redelivery).isNotNull();
            assertThat(redelivery.getEnvelope().isRedeliver()).isTrue();
            assertThat(redelivery.getProps().getMessageId())
                    .isEqualTo(request.getMessageProperties().getMessageId());
            assertThat(redelivery.getProps().getCorrelationId()).isEqualTo("51");
            assertThat(redelivery.getBody()).isEqualTo(request.getBody());
            secondConsumer.basicAck(
                    redelivery.getEnvelope().getDeliveryTag(),
                    false
            );
        }
    }

    private GetResponse awaitDelivery(Channel channel) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        GetResponse response;
        do {
            response = channel.basicGet(JOB_QUEUE.taskQueue(), false);
            if (response != null) {
                return response;
            }
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        return null;
    }

    private ConnectionFactory connectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBIT.getHost());
        factory.setPort(RABBIT.getAmqpPort());
        factory.setUsername(RABBIT.getAdminUsername());
        factory.setPassword(RABBIT.getAdminPassword());
        factory.setVirtualHost("/");
        return factory;
    }

    private Message requestMessage() {
        String body = """
                {"schemaVersion":1,"jobId":"51","jobType":"PHOTO_JUDGE",
                 "deadlineAt":"2026-11-13T22:00:00+09:00","request":{}}
                """;
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId("photo-evaluation-request-51");
        properties.setCorrelationId("51");
        properties.setType(AiJobType.PHOTO_JUDGE.name());
        properties.setHeader(OutboxPublisher.RETRY_COUNT_HEADER, 0);
        return new Message(
                body.getBytes(StandardCharsets.UTF_8),
                properties
        );
    }
}
