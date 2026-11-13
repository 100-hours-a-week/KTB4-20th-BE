package com.planit.photomission.repository;

import com.planit.photomission.domain.MissionGenerationJob;
import com.planit.photomission.domain.MissionGenerationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MissionGenerationJobRepository
        extends JpaRepository<MissionGenerationJob, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM MissionGenerationJob job WHERE job.id = :id")
    Optional<MissionGenerationJob> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT job
            FROM MissionGenerationJob job
            WHERE job.trip.id = :tripId
            """)
    Optional<MissionGenerationJob> findByTripIdForUpdate(
            @Param("tripId") Long tripId
    );

    /**
     * 메시지 큐로 접수한(QUEUED) 작업만 종료한다. 여러 경로가 같은 작업을 끝낼 수 있으므로
     * 0행이면 다른 경로가 먼저 끝낸 것이다.
     *
     * @param status SUCCEEDED 또는 FAILED
     * @return 바꾼 행 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE MissionGenerationJob job
            SET job.status = :status
            WHERE job.id = :id
              AND job.status = com.planit.photomission.domain.MissionGenerationStatus.QUEUED
            """)
    int finishQueued(
            @Param("id") Long id,
            @Param("status") MissionGenerationStatus status
    );

    /**
     * Stale Job Checker가 실패 처리할 작업을 찾는다. 기준 시각 전에 접수했는데 아직 진행 중인 작업이다.
     *
     * @param attemptedBefore 지금 이전의 가장 최근 06:00
     */
    @Query("""
            SELECT job.id
            FROM MissionGenerationJob job
            WHERE job.status = com.planit.photomission.domain.MissionGenerationStatus.QUEUED
              AND job.lastAttemptedAt < :attemptedBefore
            ORDER BY job.id
            """)
    List<Long> findQueuedIdsAttemptedBefore(@Param("attemptedBefore") LocalDateTime attemptedBefore);
}
