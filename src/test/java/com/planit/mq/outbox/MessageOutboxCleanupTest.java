package com.planit.mq.outbox;

import com.planit.mq.job.AiJobType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 정리는 행마다 별도 트랜잭션으로 지우므로 테스트 데이터도 커밋해서 만든다. */
@SpringBootTest(properties = "planit.mq.outbox.cleanup.delete-batch-size=2")
class MessageOutboxCleanupTest {

    /** 커밋된 PENDING 행을 백그라운드 Relay 스케줄러가 가져가지 않도록 미래 시각으로 둔다. */
    private static final LocalDateTime NOW = LocalDateTime.of(2099, 1, 1, 3, 0);

    @Autowired
    private MessageOutboxCleanup cleanup;

    @Autowired
    private MessageOutboxRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> savedIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        repository.deleteAllById(repository.findAllById(savedIds).stream().map(MessageOutbox::getId).toList());
    }

    @DisplayName("발행 완료 행은 삭제 건수보다 많아도 모두 지우고, PENDING 행은 남긴다")
    @Test
    void deletesAllPublishedInBatches() {
        List<Long> published = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            published.add(saveWithStatus(MessageOutboxStatus.PUBLISHED, NOW.minusDays(1)));
        }
        Long pending = saveWithStatus(MessageOutboxStatus.PENDING, NOW.minusDays(60));

        assertThat(cleanup.cleanup(NOW)).isGreaterThanOrEqualTo(5);

        assertThat(repository.findAllById(published)).isEmpty();
        assertThat(repository.findById(pending)).isPresent();
    }

    @DisplayName("발행 실패 행은 보관 기간(30일)이 지난 것만 지운다")
    @Test
    void deletesFailedAfterRetention() {
        Long expired = saveWithStatus(MessageOutboxStatus.FAILED, NOW.minusDays(30).minusSeconds(1));
        Long retained = saveWithStatus(MessageOutboxStatus.FAILED, NOW.minusDays(30));

        cleanup.cleanup(NOW);

        assertThat(repository.findById(expired)).isEmpty();
        assertThat(repository.findById(retained)).isPresent();
    }

    private Long saveWithStatus(MessageOutboxStatus status, LocalDateTime createdAt) {
        return transactionTemplate.execute(tx -> {
            MessageOutbox saved = repository.save(MessageOutbox.pending(
                    AiJobType.SCHEDULE_GENERATE,
                    42L,
                    "ai.request",
                    "ai-schedule-generation",
                    "{}",
                    createdAt,
                    NOW.plusDays(1)
            ));
            savedIds.add(saved.getId());
            switch (status) {
                case PUBLISHED -> repository.markPublished(saved.getId(), createdAt);
                case FAILED -> repository.markFailed(saved.getId(), "RETURNED: 312 NO_ROUTE");
                case PENDING -> {
                }
            }
            return saved.getId();
        });
    }
}
