package com.planit.mq.result;

import com.planit.mq.job.AiJobType;
import com.planit.mq.outbox.OutboxPublisher;
import com.planit.mq.topology.AiExchange;
import com.planit.mq.topology.AiResultQueue;
import com.planit.photomission.domain.PhotoEvaluationResult;
import com.planit.photomission.service.PhotoEvaluationCompletedResult;
import com.planit.photomission.service.PhotoEvaluationResultTransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** 실제 일정 생성 Handler 대신 받은 결과를 기록하는 Handler를 같은 Bean 이름으로 끼운다. */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@Testcontainers
class AiResultListenerIntegrationTest {

    /** 이 jobId의 결과는 처음 한 번 일시 오류를 낸다. */
    private static final long FAIL_ONCE_JOB_ID = 2L;

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
    private ScheduleResultRecorder recorder;

    @MockitoBean
    private PhotoEvaluationResultTransactionService photoEvaluationResultTransactionService;

    @AfterEach
    void tearDown() {
        recorder.handled.clear();
        rabbitTemplate.execute(channel -> channel.queuePurge(AiResultQueue.DLQ));
    }

    @DisplayName("응답 큐로 온 결과를 작업 종류의 Handler에 넘기고 ACK한다")
    @Test
    void handlesResult() throws Exception {
        sendResult(result(1L, "SCHEDULE_GENERATE"));

        AiResultEnvelope handled = recorder.handled.poll(5, TimeUnit.SECONDS);
        assertThat(handled).isNotNull();
        assertThat(handled.jobId()).isEqualTo(1L);
        assertThat(taskQueueMessageCount()).isZero();
    }

    @DisplayName("PHOTO_JUDGE 결과를 포토미션 판정 Handler에 넘겨 반영한다")
    @Test
    void handlesPhotoEvaluationResult() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setHeader(OutboxPublisher.RETRY_COUNT_HEADER, 1);
        rabbitTemplate.send(AiExchange.RESULT, AiResultQueue.ROUTING_KEY,
                new Message(photoEvaluationResult().getBytes(StandardCharsets.UTF_8), properties));

        ArgumentCaptor<PhotoEvaluationCompletedResult> resultCaptor =
                ArgumentCaptor.forClass(PhotoEvaluationCompletedResult.class);
        verify(photoEvaluationResultTransactionService, timeout(5_000))
                .applyCompleted(resultCaptor.capture());

        PhotoEvaluationCompletedResult applied = resultCaptor.getValue();
        assertThat(applied.evaluationId()).isEqualTo(51L);
        assertThat(applied.executionAttemptCount()).isEqualTo(2);
        assertThat(applied.result()).isEqualTo(PhotoEvaluationResult.SUCCESS);
        assertThat(applied.matchScore()).isEqualByComparingTo("95.0");
    }

    @DisplayName("Handler에서 일시 오류가 나면 Result Retry Queue를 거쳐 다시 받아 반영한다")
    @Test
    void retriesThroughResultRetryQueue() throws Exception {
        sendResult(result(FAIL_ONCE_JOB_ID, "SCHEDULE_GENERATE"));

        AiResultEnvelope handled = recorder.handled.poll(10, TimeUnit.SECONDS);
        assertThat(handled).isNotNull();
        assertThat(handled.jobId()).isEqualTo(FAIL_ONCE_JOB_ID);
        assertThat(rabbitTemplate.receive(AiResultQueue.DLQ, 500)).isNull();
    }

    @DisplayName("읽을 수 없는 메시지는 원인을 붙여 응답 DLQ로 보낸다")
    @Test
    void deadLettersInvalidResult() {
        sendResult("not json");
        assertThat(errorKindOf(rabbitTemplate.receive(AiResultQueue.DLQ, 5_000))).isEqualTo("INVALID_MESSAGE");
        assertThat(recorder.handled).isEmpty();
    }

    private void sendResult(String body) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        rabbitTemplate.send(AiExchange.RESULT, AiResultQueue.ROUTING_KEY,
                new Message(body.getBytes(StandardCharsets.UTF_8), properties));
    }

    private long taskQueueMessageCount() {
        return rabbitTemplate.execute(channel -> channel.queueDeclarePassive(AiResultQueue.TASK_QUEUE).getMessageCount());
    }

    private static String errorKindOf(Message message) {
        assertThat(message).isNotNull();
        return message.getMessageProperties().getHeader(AiResultRepublisher.ERROR_KIND_HEADER);
    }

    private static String result(long jobId, String jobType) {
        return """
                {"schemaVersion":1,"jobId":"%d","jobType":"%s","requestMessageId":"m-%d",
                 "status":"SUCCEEDED","errorKind":null,"result":{"status_code":200}}
                """.formatted(jobId, jobType, jobId);
    }

    private static String photoEvaluationResult() {
        return """
                {"schemaVersion":1,"jobId":"51","jobType":"PHOTO_JUDGE",
                 "requestMessageId":"photo-51","status":"SUCCEEDED","errorKind":null,
                 "result":{"result":"success","reason":null,"match_score":95.0,
                 "detected_labels":[{"name":"첨성대","matched":true}],
                 "retry_hint":null}}
                """;
    }

    @TestConfiguration
    static class HandlerConfig {

        @Bean(name = "scheduleGenerationResultHandler")
        ScheduleResultRecorder scheduleResultRecorder() {
            return new ScheduleResultRecorder();
        }
    }

    static class ScheduleResultRecorder implements AiResultHandler {

        private final BlockingQueue<AiResultEnvelope> handled = new LinkedBlockingQueue<>();
        private final Set<Long> failedOnce = ConcurrentHashMap.newKeySet();

        @Override
        public AiJobType jobType() {
            return AiJobType.SCHEDULE_GENERATE;
        }

        @Override
        public void handle(AiResultEnvelope result, int retryCount) {
            if (result.jobId() == FAIL_ONCE_JOB_ID && failedOnce.add(result.jobId())) {
                throw new IllegalStateException("일시 오류");
            }
            handled.add(result);
        }
    }
}
