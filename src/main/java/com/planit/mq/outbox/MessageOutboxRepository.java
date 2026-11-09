package com.planit.mq.outbox;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

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

    /** 발행할 차례가 된 PENDING 메시지 ID를 오래된 순으로 조회한다. */
    @Query("""
            SELECT outbox.id
            FROM MessageOutbox outbox
            WHERE outbox.status = com.planit.mq.outbox.MessageOutboxStatus.PENDING
              AND outbox.nextAttemptAt <= :now
            ORDER BY outbox.id
            """)
    List<Long> findRelayTargetIds(@Param("now") LocalDateTime now, Limit limit);

    /**
     * 다음 발행 시각을 `leaseUntil`로 미뤄 선점한다. 그동안 다른 Relay 틱은 같은 메시지를 조회하지 않는다.
     * 조회 이후 다른 경로가 먼저 처리했거나 시각을 미룬 메시지는 선점하지 않는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE MessageOutbox outbox
            SET outbox.nextAttemptAt = :leaseUntil
            WHERE outbox.id IN :ids
              AND outbox.status = com.planit.mq.outbox.MessageOutboxStatus.PENDING
              AND outbox.nextAttemptAt <= :now
            """)
    int claim(
            @Param("ids") Collection<Long> ids,
            @Param("now") LocalDateTime now,
            @Param("leaseUntil") LocalDateTime leaseUntil
    );

    /** {@link #claim}으로 이번에 선점한 메시지만 조회한다. */
    @Query("""
            SELECT outbox
            FROM MessageOutbox outbox
            WHERE outbox.id IN :ids
              AND outbox.status = com.planit.mq.outbox.MessageOutboxStatus.PENDING
              AND outbox.nextAttemptAt = :leaseUntil
            ORDER BY outbox.id
            """)
    List<MessageOutbox> findClaimed(
            @Param("ids") Collection<Long> ids,
            @Param("leaseUntil") LocalDateTime leaseUntil
    );

    /** 발행 완료 메시지를 오래된 순으로 최대 `limit`건 지운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM message_outbox
            WHERE status = 'PUBLISHED'
            ORDER BY id
            LIMIT :limit
            """, nativeQuery = true)
    int deletePublished(@Param("limit") int limit);

    /** `createdBefore` 전에 만든 발행 실패 메시지를 오래된 순으로 최대 `limit`건 지운다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM message_outbox
            WHERE status = 'FAILED'
              AND created_at < :createdBefore
            ORDER BY id
            LIMIT :limit
            """, nativeQuery = true)
    int deleteFailedCreatedBefore(
            @Param("createdBefore") LocalDateTime createdBefore,
            @Param("limit") int limit
    );
}
