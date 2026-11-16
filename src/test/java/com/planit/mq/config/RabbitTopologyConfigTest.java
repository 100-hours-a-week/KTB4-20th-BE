package com.planit.mq.config;

import com.planit.mq.topology.AiJobQueue;
import com.planit.mq.topology.AiResultQueue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class RabbitTopologyConfigTest {

    private final Declarables topology = new RabbitTopologyConfig().aiMessageTopology();
    private final Map<String, Queue> queues = topology.getDeclarablesByType(Queue.class).stream()
            .collect(Collectors.toMap(Queue::getName, Function.identity()));

    @DisplayName("direct exchange 4개를 영속으로 선언한다")
    @Test
    void declaresExchanges() {
        List<DirectExchange> exchanges = topology.getDeclarablesByType(DirectExchange.class);

        assertThat(exchanges).extracting(DirectExchange::getName)
                .containsExactlyInAnyOrder("ai.request", "ai.retry", "ai.dlx", "ai.result");
        assertThat(exchanges).allMatch(exchange -> exchange.isDurable() && !exchange.isAutoDelete());
    }

    @DisplayName("작업 3종과 응답에 대해 Task, Retry 3단계, DLQ 큐 20개를 Quorum Queue로 선언한다")
    @Test
    void declaresQuorumQueues() {
        assertThat(queues).hasSize(20);
        assertThat(queues.values()).allMatch(queue -> queue.isDurable()
                && "quorum".equals(queue.getArguments().get("x-queue-type")));
    }

    @DisplayName("Task Queue는 가득 차면 발행을 거절하고, 재전달 5회를 넘으면 DLQ로 at-least-once dead-letter한다")
    @Test
    void configuresTaskQueue() {
        assertThat(queues.get("ai.schedule-generation.task").getArguments()).contains(
                entry("x-max-length", 3_450L),
                entry("x-overflow", "reject-publish"),
                entry("x-dead-letter-exchange", "ai.dlx"),
                entry("x-dead-letter-routing-key", "ai-schedule-generation.dlq"),
                entry("x-dead-letter-strategy", "at-least-once"),
                entry("x-delivery-limit", 5)
        );
    }

    @DisplayName("Retry Queue는 Task Queue와 같은 크기로 제한하고, TTL이 끝나면 ai.request로 돌려보낸다")
    @Test
    void configuresRetryQueues() {
        AiJobQueue job = AiJobQueue.PHOTO_MISSION_GENERATE;

        assertThat(List.of(1, 2, 3)).allSatisfy(stage -> {
            Map<String, Object> arguments = queues.get(job.retryQueue(stage)).getArguments();
            assertThat(arguments).contains(
                    entry("x-max-length", 10_500L),
                    entry("x-overflow", "reject-publish"),
                    entry("x-message-ttl", AiJobQueue.RETRY_TTL_MILLIS.get(stage - 1)),
                    entry("x-dead-letter-exchange", "ai.request"),
                    entry("x-dead-letter-routing-key", "ai-photomission-generation"),
                    entry("x-dead-letter-strategy", "at-least-once")
            );
        });
        assertThat(queues).containsKeys(
                "ai.photomission-generation.retry.5s",
                "ai.photomission-generation.retry.10s",
                "ai.photomission-generation.retry.20s"
        );
    }

    @DisplayName("작업별 Task Queue와 DLQ 크기를 설계값으로 제한한다")
    @Test
    void limitsQueueLengthPerJob() {
        assertThat(queues.get("ai.photomission-verification.task").getArguments())
                .containsEntry("x-max-length", 100L);
        assertThat(queues.get("ai.schedule-generation.dlq").getArguments())
                .containsEntry("x-max-length", 50_000L)
                .containsEntry("x-overflow", "reject-publish")
                .doesNotContainKey("x-dead-letter-exchange");
        assertThat(queues.get("ai.photomission-generation.dlq").getArguments())
                .containsEntry("x-max-length", 10_500L);
        assertThat(queues.get("ai.photomission-verification.dlq").getArguments())
                .containsEntry("x-max-length", 50_000L);
    }

    @DisplayName("응답 큐는 200MB로 제한하고, Retry Queue는 1s, 2s, 4s 뒤 ai.result로 돌려보낸다")
    @Test
    void configuresResultQueues() {
        assertThat(queues.get(AiResultQueue.TASK_QUEUE).getArguments()).contains(
                entry("x-max-length-bytes", 209_715_200),
                entry("x-overflow", "reject-publish"),
                entry("x-dead-letter-exchange", "ai.dlx"),
                entry("x-dead-letter-routing-key", "ai-result.dlq"),
                entry("x-delivery-limit", 5)
        );
        assertThat(queues.get("ai.result.retry.1s").getArguments()).contains(
                entry("x-message-ttl", 1_000),
                entry("x-dead-letter-exchange", "ai.result"),
                entry("x-dead-letter-routing-key", "ai-result")
        );
        assertThat(queues).containsKeys("ai.result.retry.2s", "ai.result.retry.4s", "ai.result.dlq");
    }

    @DisplayName("각 큐를 계약 문서의 exchange와 routing key로 binding한다")
    @Test
    void bindsQueues() {
        Map<String, String> bindings = topology.getDeclarablesByType(Binding.class).stream()
                .collect(Collectors.toMap(
                        Binding::getDestination,
                        binding -> binding.getExchange() + "/" + binding.getRoutingKey()
                ));

        assertThat(bindings).hasSize(20).contains(
                entry("ai.schedule-generation.task", "ai.request/ai-schedule-generation"),
                entry("ai.schedule-generation.retry.5s", "ai.retry/ai-schedule-generation.retry.1"),
                entry("ai.schedule-generation.retry.20s", "ai.retry/ai-schedule-generation.retry.3"),
                entry("ai.schedule-generation.dlq", "ai.dlx/ai-schedule-generation.dlq"),
                entry("ai.photomission-verification.task", "ai.request/ai-photomission-verification"),
                entry("ai.result.task", "ai.result/ai-result"),
                entry("ai.result.retry.4s", "ai.retry/ai-result.retry.3"),
                entry("ai.result.dlq", "ai.dlx/ai-result.dlq")
        );
    }
}
