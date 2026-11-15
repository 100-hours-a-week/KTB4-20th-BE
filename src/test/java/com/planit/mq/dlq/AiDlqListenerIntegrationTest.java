package com.planit.mq.dlq;

import com.planit.mq.job.AiJobTerminationService;
import com.planit.mq.job.AiJobType;
import com.planit.mq.topology.AiExchange;
import com.planit.mq.topology.AiJobQueue;
import com.rabbitmq.client.GetResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Testcontainers
class AiDlqListenerIntegrationTest {

    private static final AiJobQueue QUEUE = AiJobQueue.SCHEDULE_GENERATE;

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

    @MockitoBean
    private AiJobTerminationService terminationService;

    @DisplayName("AI Worker가 실패 원인과 함께 보낸 요청 DLQ 메시지로 작업을 실패 처리한다")
    @Test
    void failsJobWithErrorKind() {
        MessageProperties properties = requestProperties("41");
        properties.setHeader(AiDlqListener.ERROR_KIND_HEADER, "AI_UNAVAILABLE");
        rabbitTemplate.send(AiExchange.DLX, QUEUE.dlqRoutingKey(), new Message(body(), properties));

        verify(terminationService, timeout(5_000)).fail(AiJobType.SCHEDULE_GENERATE, 41L, "AI_UNAVAILABLE");
    }

    @DisplayName("재전달 한도를 넘어 RabbitMQ가 dead-letter한 메시지는 DELIVERY_LIMIT으로 실패 처리한다")
    @Test
    void failsJobOnDeliveryLimit() {
        rabbitTemplate.send(AiExchange.REQUEST, QUEUE.routingKey(), new Message(body(), requestProperties("42")));
        rejectUntilDeadLettered();

        verify(terminationService, timeout(5_000)).fail(AiJobType.SCHEDULE_GENERATE, 42L, "DELIVERY_LIMIT");
    }

    /** AI Worker가 처리 중 계속 죽는 상황처럼 작업 큐에서 꺼내 되돌리기를 반복한다. */
    private void rejectUntilDeadLettered() {
        rabbitTemplate.execute(channel -> {
            for (int attempt = 0; attempt <= AiJobQueue.DELIVERY_LIMIT + 1; attempt++) {
                GetResponse response = channel.basicGet(QUEUE.taskQueue(), false);
                if (response == null) {
                    break;
                }
                channel.basicNack(response.getEnvelope().getDeliveryTag(), false, true);
            }
            return null;
        });
    }

    private static MessageProperties requestProperties(String jobId) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setMessageId("request-" + jobId);
        properties.setCorrelationId(jobId);
        properties.setType(AiJobType.SCHEDULE_GENERATE.name());
        return properties;
    }

    private static byte[] body() {
        return "{\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8);
    }
}
