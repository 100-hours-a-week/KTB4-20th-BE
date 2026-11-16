package com.planit.mq.config;

import com.planit.mq.topology.AiExchange;
import com.planit.mq.topology.AiJobQueue;
import com.planit.mq.topology.AiResultQueue;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 작업 exchange, 큐, binding 선언.
 * 백엔드만 선언하며, RabbitAdmin이 최초 연결 시 멱등적으로 생성한다. AI 서버 계정에는 선언 권한이 없다.
 * 기존 큐와 인자가 다르면 선언이 실패하므로, 인자를 바꿀 때는 큐를 지우고 다시 만든다.
 */
@Configuration(proxyBeanMethods = false)
public class RabbitTopologyConfig {

    static final String DEAD_LETTER_STRATEGY = "x-dead-letter-strategy";
    static final String AT_LEAST_ONCE = "at-least-once";

    @Bean
    public Declarables aiMessageTopology() {
        DirectExchange request = exchange(AiExchange.REQUEST);
        DirectExchange retry = exchange(AiExchange.RETRY);
        DirectExchange dlx = exchange(AiExchange.DLX);
        DirectExchange result = exchange(AiExchange.RESULT);

        List<Declarable> declarables = new ArrayList<>(List.of(request, retry, dlx, result));
        for (AiJobQueue job : AiJobQueue.values()) {
            declarables.addAll(jobQueues(job, request, retry, dlx));
        }
        declarables.addAll(resultQueues(result, retry, dlx));
        return new Declarables(declarables);
    }

    private List<Declarable> jobQueues(
            AiJobQueue job,
            DirectExchange request,
            DirectExchange retry,
            DirectExchange dlx
    ) {
        List<Declarable> declarables = new ArrayList<>();

        Queue task = QueueBuilder.durable(job.taskQueue())
                .quorum()
                .maxLength(job.maxLength())
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .deadLetterExchange(AiExchange.DLX)
                .deadLetterRoutingKey(job.dlqRoutingKey())
                .withArgument(DEAD_LETTER_STRATEGY, AT_LEAST_ONCE)
                .deliveryLimit(AiJobQueue.DELIVERY_LIMIT)
                .build();
        declarables.add(task);
        declarables.add(bind(task, request, job.routingKey()));

        for (int stage = 1; stage <= AiJobQueue.RETRY_TTL_MILLIS.size(); stage++) {
            Queue retryQueue = QueueBuilder.durable(job.retryQueue(stage))
                    .quorum()
                    .maxLength(job.maxLength())
                    .overflow(QueueBuilder.Overflow.rejectPublish)
                    .ttl(AiJobQueue.RETRY_TTL_MILLIS.get(stage - 1))
                    .deadLetterExchange(AiExchange.REQUEST)
                    .deadLetterRoutingKey(job.routingKey())
                    .withArgument(DEAD_LETTER_STRATEGY, AT_LEAST_ONCE)
                    .build();
            declarables.add(retryQueue);
            declarables.add(bind(retryQueue, retry, job.retryRoutingKey(stage)));
        }

        Queue dlq = QueueBuilder.durable(job.dlq())
                .quorum()
                .maxLength(job.dlqMaxLength())
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .build();
        declarables.add(dlq);
        declarables.add(bind(dlq, dlx, job.dlqRoutingKey()));

        return declarables;
    }

    private List<Declarable> resultQueues(
            DirectExchange result,
            DirectExchange retry,
            DirectExchange dlx
    ) {
        List<Declarable> declarables = new ArrayList<>();

        Queue task = QueueBuilder.durable(AiResultQueue.TASK_QUEUE)
                .quorum()
                .maxLengthBytes(AiResultQueue.MAX_LENGTH_BYTES)
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .deadLetterExchange(AiExchange.DLX)
                .deadLetterRoutingKey(AiResultQueue.DLQ_ROUTING_KEY)
                .withArgument(DEAD_LETTER_STRATEGY, AT_LEAST_ONCE)
                .deliveryLimit(AiJobQueue.DELIVERY_LIMIT)
                .build();
        declarables.add(task);
        declarables.add(bind(task, result, AiResultQueue.ROUTING_KEY));

        for (int stage = 1; stage <= AiResultQueue.RETRY_TTL_MILLIS.size(); stage++) {
            Queue retryQueue = QueueBuilder.durable(AiResultQueue.retryQueue(stage))
                    .quorum()
                    .maxLengthBytes(AiResultQueue.MAX_LENGTH_BYTES)
                    .overflow(QueueBuilder.Overflow.rejectPublish)
                    .ttl(AiResultQueue.RETRY_TTL_MILLIS.get(stage - 1))
                    .deadLetterExchange(AiExchange.RESULT)
                    .deadLetterRoutingKey(AiResultQueue.ROUTING_KEY)
                    .withArgument(DEAD_LETTER_STRATEGY, AT_LEAST_ONCE)
                    .build();
            declarables.add(retryQueue);
            declarables.add(bind(retryQueue, retry, AiResultQueue.retryRoutingKey(stage)));
        }

        Queue dlq = QueueBuilder.durable(AiResultQueue.DLQ)
                .quorum()
                .maxLengthBytes(AiResultQueue.MAX_LENGTH_BYTES)
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .build();
        declarables.add(dlq);
        declarables.add(bind(dlq, dlx, AiResultQueue.DLQ_ROUTING_KEY));

        return declarables;
    }

    private DirectExchange exchange(String name) {
        return new DirectExchange(name, true, false);
    }

    private Binding bind(Queue queue, DirectExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}
