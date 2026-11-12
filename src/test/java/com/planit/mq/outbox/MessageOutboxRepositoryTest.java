package com.planit.mq.outbox;

import com.planit.mq.job.AiJobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MessageOutboxRepositoryTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 9, 10, 0);

    @Autowired
    private MessageOutboxRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DisplayName("발행 대기 메시지를 PENDING, 재시도 0회로 저장하고 JSON 본문과 메시지 ID를 그대로 읽는다")
    @Test
    void savesPendingMessage() {
        String payload = "{\"schemaVersion\": 1, \"jobId\": \"10\"}";
        MessageOutbox saved = repository.saveAndFlush(MessageOutbox.pending(
                AiJobType.SCHEDULE_GENERATE,
                10L,
                "ai.request",
                "ai-schedule-generation",
                payload,
                CREATED_AT,
                CREATED_AT.plusSeconds(10)
        ));

        MessageOutbox found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(MessageOutboxStatus.PENDING);
        assertThat(found.getMessageId()).isEqualTo(saved.getMessageId());
        assertThat(found.getNackAttemptCount()).isZero();
        assertThat(found.getFailureAttemptCount()).isZero();
        assertThat(found.getNextAttemptAt()).isEqualTo(CREATED_AT.plusSeconds(10));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT JSON_EXTRACT(payload, '$.jobId') FROM message_outbox WHERE id = ?",
                String.class,
                saved.getId()
        )).isEqualTo("\"10\"");
    }

    @DisplayName("같은 메시지 ID는 두 번 저장할 수 없다")
    @Test
    void rejectsDuplicateMessageId() {
        MessageOutbox first = repository.saveAndFlush(outbox(1L));
        MessageOutbox second = repository.saveAndFlush(outbox(2L));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE message_outbox SET message_id = (SELECT message_id FROM "
                        + "(SELECT message_id FROM message_outbox WHERE id = ?) AS source) WHERE id = ?",
                first.getId(),
                second.getId()
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @DisplayName("다음 발행 시각이 생성 시각보다 이르면 만들 수 없다")
    @Test
    void rejectsNextAttemptBeforeCreation() {
        assertThatThrownBy(() -> MessageOutbox.pending(
                AiJobType.PHOTO_JUDGE,
                1L,
                "ai.request",
                "ai-photomission-verification",
                "{}",
                CREATED_AT,
                CREATED_AT.minusSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("PENDING 메시지를 발행 완료로 바꾸고, 이미 바뀐 메시지는 다시 바꾸지 않는다")
    @Test
    void marksPublishedOnlyOnce() {
        MessageOutbox saved = repository.saveAndFlush(outbox(1L));
        LocalDateTime publishedAt = CREATED_AT.plusSeconds(1);

        assertThat(repository.markPublished(saved.getId(), publishedAt)).isOne();
        assertThat(repository.markPublished(saved.getId(), publishedAt.plusSeconds(1))).isZero();

        MessageOutbox found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(MessageOutboxStatus.PUBLISHED);
        assertThat(found.getPublishedAt()).isEqualTo(publishedAt);
    }

    @DisplayName("NACK 재시도는 NACK 횟수만 올리고 다음 발행 시각과 실패 이유를 남긴다")
    @Test
    void schedulesNackRetry() {
        MessageOutbox saved = repository.saveAndFlush(outbox(1L));
        LocalDateTime nextAttemptAt = CREATED_AT.plusSeconds(20);

        assertThat(repository.scheduleNackRetry(saved.getId(), nextAttemptAt, "NACK: reject-publish")).isOne();
        assertThat(repository.scheduleNackRetry(saved.getId(), nextAttemptAt, "NACK: reject-publish")).isOne();

        MessageOutbox found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(MessageOutboxStatus.PENDING);
        assertThat(found.getNackAttemptCount()).isEqualTo(2);
        assertThat(found.getFailureAttemptCount()).isZero();
        assertThat(found.getNextAttemptAt()).isEqualTo(nextAttemptAt);
        assertThat(found.getLastError()).isEqualTo("NACK: reject-publish");
    }

    @DisplayName("confirm 유실 재시도는 유실 횟수만 올린다")
    @Test
    void schedulesFailureRetry() {
        MessageOutbox saved = repository.saveAndFlush(outbox(1L));

        assertThat(repository.scheduleFailureRetry(
                saved.getId(), CREATED_AT.plusSeconds(20), "TIMEOUT: confirm not received"
        )).isOne();

        MessageOutbox found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getFailureAttemptCount()).isOne();
        assertThat(found.getNackAttemptCount()).isZero();
    }

    @DisplayName("실패 처리된 메시지는 발행 완료나 재시도로 바꾸지 않는다")
    @Test
    void ignoresUpdatesAfterFailed() {
        MessageOutbox saved = repository.saveAndFlush(outbox(1L));

        assertThat(repository.markFailed(saved.getId(), "RETURNED: NO_ROUTE")).isOne();
        assertThat(repository.markPublished(saved.getId(), CREATED_AT)).isZero();
        assertThat(repository.scheduleNackRetry(saved.getId(), CREATED_AT, "NACK")).isZero();
        assertThat(repository.scheduleFailureRetry(saved.getId(), CREATED_AT, "TIMEOUT")).isZero();
        assertThat(repository.markFailed(saved.getId(), "again")).isZero();

        MessageOutbox found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(MessageOutboxStatus.FAILED);
        assertThat(found.getLastError()).isEqualTo("RETURNED: NO_ROUTE");
    }

    @DisplayName("작업의 발행 대기 Outbox만 실패로 바꾸고, 이미 발행한 Outbox와 다른 작업의 Outbox는 그대로 둔다")
    @Test
    void failsPendingOutboxOfJob() {
        MessageOutbox pending = repository.saveAndFlush(outbox(1L));
        MessageOutbox published = repository.saveAndFlush(outbox(1L));
        repository.markPublished(published.getId(), CREATED_AT.plusSeconds(1));
        MessageOutbox otherJob = repository.saveAndFlush(outbox(2L));

        assertThat(repository.markFailedPendingByJob(AiJobType.PHOTO_MISSION_GENERATE, 1L, "STALE")).isOne();

        MessageOutbox failed = repository.findById(pending.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(MessageOutboxStatus.FAILED);
        assertThat(failed.getLastError()).isEqualTo("STALE");
        assertThat(repository.findById(published.getId()).orElseThrow().getStatus())
                .isEqualTo(MessageOutboxStatus.PUBLISHED);
        assertThat(repository.findById(otherJob.getId()).orElseThrow().getStatus())
                .isEqualTo(MessageOutboxStatus.PENDING);
        assertThat(repository.markFailedPendingByJob(AiJobType.SCHEDULE_GENERATE, 2L, "STALE")).isZero();
    }

    private MessageOutbox outbox(Long jobId) {
        return MessageOutbox.pending(
                AiJobType.PHOTO_MISSION_GENERATE,
                jobId,
                "ai.request",
                "ai-photomission-generation",
                "{}",
                CREATED_AT,
                CREATED_AT.plusSeconds(10)
        );
    }
}
