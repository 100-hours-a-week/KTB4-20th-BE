package com.planit.mq.outbox;

import com.planit.mq.job.AiJobType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 작업 행과 같은 트랜잭션에 저장하는 발행 대기 메시지.
 * 상태 변경은 여러 경로(즉시 발행, Relay, Stale)가 동시에 할 수 있으므로
 * 엔티티 수정 대신 Repository의 `status = 'PENDING'` 조건부 UPDATE로 처리한다.
 */
@Entity
@Table(
        name = "message_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_message_outbox_message_id",
                columnNames = "message_id"
        )
)
public class MessageOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 40)
    private AiJobType jobType;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "message_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID messageId;

    @Column(name = "exchange_name", nullable = false, length = 100)
    private String exchangeName;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MessageOutboxStatus status;

    @Column(name = "nack_attempt_count", nullable = false)
    private byte nackAttemptCount;

    @Column(name = "failure_attempt_count", nullable = false)
    private byte failureAttemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    protected MessageOutbox() {
    }

    /**
     * 발행 대기 메시지를 만든다.
     *
     * @param nextAttemptAt Relay가 이 메시지를 처음 재발행할 수 있는 시각.
     *                      커밋 직후 발행의 confirm을 기다리는 동안 Relay가 다시 발행하지 않도록 confirm 대기시간보다 뒤로 둔다.
     */
    public static MessageOutbox pending(
            AiJobType jobType,
            Long jobId,
            String exchangeName,
            String routingKey,
            String payload,
            LocalDateTime createdAt,
            LocalDateTime nextAttemptAt
    ) {
        MessageOutbox outbox = new MessageOutbox();
        outbox.jobType = jobType;
        outbox.jobId = jobId;
        outbox.messageId = UUID.randomUUID();
        outbox.exchangeName = exchangeName;
        outbox.routingKey = routingKey;
        outbox.payload = payload;
        outbox.status = MessageOutboxStatus.PENDING;
        outbox.createdAt = createdAt;
        outbox.nextAttemptAt = nextAttemptAt;
        outbox.validate();
        return outbox;
    }

    private void validate() {
        requireValue(jobType, "작업 종류는 필수입니다");
        requireValue(jobId, "작업 ID는 필수입니다");
        requireText(exchangeName, "exchange 이름은 필수입니다");
        requireText(routingKey, "routing key는 필수입니다");
        requireText(payload, "메시지 본문은 필수입니다");
        requireValue(createdAt, "생성 시각은 필수입니다");
        requireValue(nextAttemptAt, "다음 발행 시각은 필수입니다");
        if (nextAttemptAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("다음 발행 시각은 생성 시각 이후여야 합니다");
        }
    }

    private static void requireValue(Object value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    public Long getId() {
        return id;
    }

    public AiJobType getJobType() {
        return jobType;
    }

    public Long getJobId() {
        return jobId;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getPayload() {
        return payload;
    }

    public MessageOutboxStatus getStatus() {
        return status;
    }

    public int getNackAttemptCount() {
        return nackAttemptCount;
    }

    public int getFailureAttemptCount() {
        return failureAttemptCount;
    }

    public LocalDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }
}
