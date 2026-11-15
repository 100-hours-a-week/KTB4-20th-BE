package com.planit.mq.outbox;

import com.planit.mq.config.RabbitMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * 커밋 직후 발행이 실패했거나 재시도 시각이 된 PENDING 메시지를 다시 발행한다.
 * 1. 발행할 차례가 된 메시지 ID를 최대 `batch-size`건 조회
 * 2. 다음 발행 시각을 confirm 대기시간 뒤로 미뤄 선점
 * 3. 선점한 메시지를 모두 먼저 발행하고, confirm은 한꺼번에 기다려 결과를 반영
 */
@Component
public class MessageOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(MessageOutboxRelay.class);

    private final MessageOutboxRepository repository;
    private final OutboxPublisher publisher;
    private final RabbitMqProperties properties;
    private final TransactionTemplate transactionTemplate;

    public MessageOutboxRelay(
            MessageOutboxRepository repository,
            OutboxPublisher publisher,
            RabbitMqProperties properties,
            TransactionTemplate transactionTemplate
    ) {
        this.repository = repository;
        this.publisher = publisher;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 모든 발행 결과를 Outbox에 반영한 뒤 돌아온다. confirm 대기시간보다 오래 걸리지 않는다.
     *
     * @return 이번에 발행한 메시지 수
     */
    public int relayDue(LocalDateTime now) {
        // DB가 마이크로초까지 저장하므로 선점한 행을 같은 값으로 다시 찾을 수 있게 맞춘다
        LocalDateTime claimedAt = now.truncatedTo(ChronoUnit.MICROS);
        List<Long> ids = repository.findRelayTargetIds(claimedAt, Limit.of(properties.outbox().relay().batchSize()));
        if (ids.isEmpty()) {
            return 0;
        }
        LocalDateTime leaseUntil = claimedAt.plus(properties.firstRelayDelay());
        List<MessageOutbox> claimed = transactionTemplate.execute(status -> {
            repository.claim(ids, claimedAt, leaseUntil);
            return repository.findClaimed(ids, leaseUntil);
        });

        List<CompletableFuture<Void>> results = claimed.stream()
                .map(publisher::publish)
                .toList();
        try {
            CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException e) {
            log.error("Relay 발행 결과 반영 중 오류, 다음 선점 시각에 다시 발행함 count={}", claimed.size(), e);
        }
        return claimed.size();
    }
}
