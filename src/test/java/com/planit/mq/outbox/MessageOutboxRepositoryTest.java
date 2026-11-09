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
