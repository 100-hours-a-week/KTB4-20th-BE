package com.planit.mq.outbox;

import com.planit.mq.config.RabbitMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.function.IntSupplier;

/**
 * 처리가 끝난 Outbox 행을 지운다. PENDING 행은 지우지 않는다.
 * - 발행 완료: 전부
 * - 발행 실패: `failed-retention`이 지난 행 (원인 확인을 위해 그동안 남긴다)
 * 한 번의 DELETE가 락을 오래 잡지 않도록 `delete-batch-size`건씩 별도 트랜잭션으로 나눠 지운다.
 */
@Component
public class MessageOutboxCleanup {

    private static final Logger log = LoggerFactory.getLogger(MessageOutboxCleanup.class);

    private final MessageOutboxRepository repository;
    private final RabbitMqProperties properties;
    private final TransactionTemplate transactionTemplate;

    public MessageOutboxCleanup(
            MessageOutboxRepository repository,
            RabbitMqProperties properties,
            TransactionTemplate transactionTemplate
    ) {
        this.repository = repository;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    /** @return 지운 행 수 */
    public int cleanup(LocalDateTime now) {
        int batchSize = properties.outbox().cleanup().deleteBatchSize();
        LocalDateTime failedBefore = now.minus(properties.outbox().failedRetention());

        int published = deleteInBatches(() -> repository.deletePublished(batchSize));
        int failed = deleteInBatches(() -> repository.deleteFailedCreatedBefore(failedBefore, batchSize));
        log.info("Outbox 정리 완료 published={}, failed={}, failedBefore={}", published, failed, failedBefore);
        return published + failed;
    }

    private int deleteInBatches(IntSupplier deleteBatch) {
        int total = 0;
        int deleted;
        do {
            deleted = transactionTemplate.execute(status -> deleteBatch.getAsInt());
            total += deleted;
        } while (deleted > 0);
        return total;
    }
}
