package com.planit.mq.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * 즉시 발행, Relay, Stale Job Checker가 같은 행을 동시에 바꿀 수 있으므로
 * 상태 변경은 모두 `status = PENDING` 조건부 UPDATE로 한다.
 * 반환값이 0이면 다른 경로가 먼저 처리한 것이므로 호출한 쪽은 아무것도 하지 않는다.
 */
public interface MessageOutboxRepository extends JpaRepository<MessageOutbox, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE MessageOutbox outbox
            SET outbox.status = com.planit.mq.outbox.MessageOutboxStatus.PUBLISHED,
                outbox.publishedAt = :publishedAt
            WHERE outbox.id = :id
              AND outbox.status = com.planit.mq.outbox.MessageOutboxStatus.PENDING
            """)
    int markPublished(@Param("id") Long id, @Param("publishedAt") LocalDateTime publishedAt);

    /** NACK 횟수를 1 올리고 다음 발행 시각을 미룬다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE MessageOutbox outbox
            SET outbox.nackAttemptCount = outbox.nackAttemptCount + 1,
                outbox.nextAttemptAt = :nextAttemptAt,
                outbox.lastError = :lastError
            WHERE outbox.id = :id
              AND outbox.status = com.planit.mq.outbox.MessageOutboxStatus.PENDING
            """)
    int scheduleNackRetry(
            @Param("id") Long id,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError
    );

    /** confirm 유실·연결 예외 횟수를 1 올리고 다음 발행 시각을 미룬다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE MessageOutbox outbox
            SET outbox.failureAttemptCount = outbox.failureAttemptCount + 1,
                outbox.nextAttemptAt = :nextAttemptAt,
                outbox.lastError = :lastError
            WHERE outbox.id = :id
              AND outbox.status = com.planit.mq.outbox.MessageOutboxStatus.PENDING
            """)
    int scheduleFailureRetry(
            @Param("id") Long id,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE MessageOutbox outbox
            SET outbox.status = com.planit.mq.outbox.MessageOutboxStatus.FAILED,
                outbox.lastError = :lastError
            WHERE outbox.id = :id
              AND outbox.status = com.planit.mq.outbox.MessageOutboxStatus.PENDING
            """)
    int markFailed(@Param("id") Long id, @Param("lastError") String lastError);
}
