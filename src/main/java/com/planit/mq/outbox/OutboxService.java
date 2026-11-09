package com.planit.mq.outbox;

import com.planit.mq.config.RabbitMqConfig;
import com.planit.mq.config.RabbitMqProperties;
import com.planit.mq.job.AiJobType;
import com.planit.mq.topology.AiExchange;
import com.planit.mq.topology.AiJobQueue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;

/** AI 작업 메시지를 Outbox에 저장하고, 작업 트랜잭션이 커밋되면 바로 발행한다. */
@Service
public class OutboxService {

    private final MessageOutboxRepository repository;
    private final OutboxPublisher publisher;
    private final RabbitMqProperties properties;
    private final Clock clock;

    public OutboxService(
            MessageOutboxRepository repository,
            OutboxPublisher publisher,
            RabbitMqProperties properties,
            @Qualifier(RabbitMqConfig.MQ_CLOCK) Clock clock
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 작업 행을 저장한 트랜잭션 안에서 호출한다. 트랜잭션 없이 부르면 예외가 난다.
     * 롤백되면 Outbox 행도 남지 않고 발행도 하지 않는다.
     *
     * @param payload 계약 문서 4.3절 요청 본문(JSON)
     * @return 저장한 Outbox 행
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public MessageOutbox enqueue(AiJobType jobType, Long jobId, String payload) {
        LocalDateTime now = LocalDateTime.now(clock);
        MessageOutbox outbox = repository.save(MessageOutbox.pending(
                jobType,
                jobId,
                AiExchange.REQUEST,
                queueOf(jobType).routingKey(),
                payload,
                now,
                now.plus(properties.firstRelayDelay())
        ));
        Long outboxId = outbox.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publishAsync(outboxId);
            }
        });
        return outbox;
    }

    private static AiJobQueue queueOf(AiJobType jobType) {
        return switch (jobType) {
            case SCHEDULE_GENERATE -> AiJobQueue.SCHEDULE_GENERATE;
            case PHOTO_MISSION_GENERATE -> AiJobQueue.PHOTO_MISSION_GENERATE;
            case PHOTO_JUDGE -> AiJobQueue.PHOTO_JUDGE;
        };
    }
}
