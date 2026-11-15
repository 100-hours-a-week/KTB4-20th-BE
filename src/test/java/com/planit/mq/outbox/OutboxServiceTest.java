package com.planit.mq.outbox;

import com.planit.mq.config.RabbitMqConfig;
import com.planit.mq.job.AiJobType;
import com.planit.mq.topology.AiExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
class OutboxServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 9, 10, 0);

    @TestBean(name = RabbitMqConfig.MQ_CLOCK, methodName = "fixedClock")
    private Clock clock;

    @MockitoBean
    private OutboxPublisher publisher;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private MessageOutboxRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> savedIds = new ArrayList<>();

    static Clock fixedClock() {
        return Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
    }

    @AfterEach
    void tearDown() {
        repository.deleteAllById(savedIds);
    }

    @DisplayName("작업 트랜잭션 안에서 저장하면 커밋한 뒤에 발행한다")
    @Test
    void publishesAfterCommit() {
        MessageOutbox saved = transactionTemplate.execute(status -> {
            MessageOutbox outbox = outboxService.enqueue(AiJobType.SCHEDULE_GENERATE, 42L, "{\"jobId\": \"42\"}");
            savedIds.add(outbox.getId());
            verify(publisher, never()).publishAsync(anyLong());
            return outbox;
        });

        verify(publisher).publishAsync(saved.getId());
        MessageOutbox found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(MessageOutboxStatus.PENDING);
        assertThat(found.getJobType()).isEqualTo(AiJobType.SCHEDULE_GENERATE);
        assertThat(found.getJobId()).isEqualTo(42L);
        assertThat(found.getExchangeName()).isEqualTo(AiExchange.REQUEST);
        assertThat(found.getRoutingKey()).isEqualTo("ai-schedule-generation");
        assertThat(found.getCreatedAt()).isEqualTo(NOW);
        assertThat(found.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @DisplayName("작업 종류별 routing key로 저장한다")
    @Test
    void usesRoutingKeyOfJobType() {
        List<MessageOutbox> saved = transactionTemplate.execute(status -> List.of(
                outboxService.enqueue(AiJobType.PHOTO_MISSION_GENERATE, 1L, "{}"),
                outboxService.enqueue(AiJobType.PHOTO_JUDGE, 2L, "{}")
        ));
        saved.forEach(outbox -> savedIds.add(outbox.getId()));

        assertThat(saved).extracting(MessageOutbox::getRoutingKey)
                .containsExactly("ai-photomission-generation", "ai-photomission-verification");
    }

    @DisplayName("작업 트랜잭션이 롤백되면 Outbox 행이 남지 않고 발행하지 않는다")
    @Test
    void doesNotPublishOnRollback() {
        Long outboxId = transactionTemplate.execute(status -> {
            MessageOutbox outbox = outboxService.enqueue(AiJobType.SCHEDULE_GENERATE, 42L, "{}");
            status.setRollbackOnly();
            return outbox.getId();
        });

        verify(publisher, never()).publishAsync(anyLong());
        assertThat(repository.findById(outboxId)).isEmpty();
    }

    @DisplayName("트랜잭션 없이 저장하면 예외가 난다")
    @Test
    void requiresTransaction() {
        assertThatThrownBy(() -> outboxService.enqueue(AiJobType.SCHEDULE_GENERATE, 42L, "{}"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
