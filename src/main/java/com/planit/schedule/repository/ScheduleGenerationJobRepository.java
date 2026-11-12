package com.planit.schedule.repository;

import com.planit.schedule.domain.ScheduleGenerationJob;
import com.planit.schedule.domain.ScheduleGenerationJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleGenerationJobRepository extends JpaRepository<ScheduleGenerationJob, Long> {

    /**
     * 진행 중(QUEUED)인 작업만 종료하고 active_slot을 비워 같은 여행의 다음 요청을 받을 수 있게 한다.
     * 결과 처리, DLQ, Stale, 발행 실패가 같은 작업을 동시에 끝낼 수 있으므로 0행이면 다른 경로가 먼저 끝낸 것이다.
     *
     * @param status    SUCCEEDED 또는 FAILED
     * @param errorCode 실패 원인. 성공이면 null
     * @return 바꾼 행 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE ScheduleGenerationJob job
            SET job.status = :status,
                job.activeSlot = NULL,
                job.finishedAt = :finishedAt,
                job.errorCode = :errorCode
            WHERE job.id = :id
              AND job.status = com.planit.schedule.domain.ScheduleGenerationJobStatus.QUEUED
            """)
    int finishQueued(
            @Param("id") Long id,
            @Param("status") ScheduleGenerationJobStatus status,
            @Param("errorCode") String errorCode,
            @Param("finishedAt") LocalDateTime finishedAt
    );

    /** 같은 Idempotency-Key로 이미 접수한 작업. 재요청이면 그 작업의 접수 응답을 그대로 돌려준다. */
    Optional<ScheduleGenerationJob> findByIdempotencyKey(UUID idempotencyKey);
}
