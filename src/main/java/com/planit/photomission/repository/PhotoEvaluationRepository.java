package com.planit.photomission.repository;

import com.planit.photomission.domain.PhotoEvaluation;
import com.planit.photomission.domain.PhotoEvaluationExecutionStatus;
import com.planit.photomission.domain.PhotoEvaluationReason;
import com.planit.photomission.domain.PhotoEvaluationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PhotoEvaluationRepository
        extends JpaRepository<PhotoEvaluation, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PhotoEvaluation evaluation
            SET evaluation.executionStatus =
                    com.planit.photomission.domain.PhotoEvaluationExecutionStatus.SUCCEEDED,
                evaluation.result = :result,
                evaluation.reason = :reason,
                evaluation.matchScore = :matchScore,
                evaluation.detectedLabelsJson = :detectedLabelsJson,
                evaluation.retryHint = :retryHint,
                evaluation.errorCode = NULL,
                evaluation.executionAttemptCount = :executionAttemptCount,
                evaluation.activeSlot = NULL,
                evaluation.evaluatedAt = :evaluatedAt,
                evaluation.retryAfterAt = NULL
            WHERE evaluation.id = :evaluationId
              AND evaluation.executionStatus =
                    com.planit.photomission.domain.PhotoEvaluationExecutionStatus.QUEUED
              AND evaluation.deadlineAt > :evaluatedAt
            """)
    int completeQueued(
            @Param("evaluationId") Long evaluationId,
            @Param("executionAttemptCount") int executionAttemptCount,
            @Param("result") PhotoEvaluationResult result,
            @Param("reason") PhotoEvaluationReason reason,
            @Param("matchScore") BigDecimal matchScore,
            @Param("detectedLabelsJson") String detectedLabelsJson,
            @Param("retryHint") String retryHint,
            @Param("evaluatedAt") LocalDateTime evaluatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PhotoEvaluation evaluation
            SET evaluation.executionStatus =
                    com.planit.photomission.domain.PhotoEvaluationExecutionStatus.FAILED,
                evaluation.errorCode = :errorCode,
                evaluation.executionAttemptCount = :executionAttemptCount,
                evaluation.activeSlot = NULL,
                evaluation.evaluatedAt = :failedAt,
                evaluation.retryAfterAt = NULL
            WHERE evaluation.id = :evaluationId
              AND evaluation.executionStatus =
                    com.planit.photomission.domain.PhotoEvaluationExecutionStatus.QUEUED
            """)
    int failQueued(
            @Param("evaluationId") Long evaluationId,
            @Param("executionAttemptCount") int executionAttemptCount,
            @Param("errorCode") String errorCode,
            @Param("failedAt") LocalDateTime failedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PhotoEvaluation evaluation
            SET evaluation.executionStatus =
                    com.planit.photomission.domain.PhotoEvaluationExecutionStatus.FAILED,
                evaluation.errorCode = :errorCode,
                evaluation.activeSlot = NULL,
                evaluation.evaluatedAt = :failedAt,
                evaluation.retryAfterAt = NULL
            WHERE evaluation.id = :evaluationId
              AND evaluation.executionStatus =
                    com.planit.photomission.domain.PhotoEvaluationExecutionStatus.QUEUED
            """)
    int failQueuedPreservingAttempt(
            @Param("evaluationId") Long evaluationId,
            @Param("errorCode") String errorCode,
            @Param("failedAt") LocalDateTime failedAt
    );

    @Query("""
            SELECT evaluation.id
            FROM PhotoEvaluation evaluation
            WHERE evaluation.executionStatus =
                    com.planit.photomission.domain.PhotoEvaluationExecutionStatus.QUEUED
              AND evaluation.deadlineAt <= :deadline
            ORDER BY evaluation.id
            """)
    List<Long> findQueuedIdsDeadlineAtOrBefore(
            @Param("deadline") LocalDateTime deadline
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE PhotoEvaluation evaluation
            SET evaluation.executionStatus =
                    com.planit.photomission.domain.PhotoEvaluationExecutionStatus.FAILED,
                evaluation.errorCode = :errorCode,
                evaluation.activeSlot = NULL,
                evaluation.evaluatedAt = :failedAt,
                evaluation.retryAfterAt = NULL
            WHERE evaluation.missionPhoto.id = :missionPhotoId
              AND evaluation.executionStatus =
                    com.planit.photomission.domain.PhotoEvaluationExecutionStatus.QUEUED
            """)
    int failQueuedByMissionPhotoId(
            @Param("missionPhotoId") Long missionPhotoId,
            @Param("errorCode") String errorCode,
            @Param("failedAt") LocalDateTime failedAt
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE PhotoEvaluation evaluation
            SET evaluation.executionStatus =
                    com.planit.photomission.domain.PhotoEvaluationExecutionStatus.FAILED,
                evaluation.errorCode = :errorCode,
                evaluation.activeSlot = NULL,
                evaluation.evaluatedAt = :failedAt,
                evaluation.retryAfterAt = NULL
            WHERE evaluation.missionPhoto.mission.trip.id = :tripId
              AND evaluation.executionStatus =
                    com.planit.photomission.domain.PhotoEvaluationExecutionStatus.QUEUED
            """)
    int failQueuedByTripId(
            @Param("tripId") Long tripId,
            @Param("errorCode") String errorCode,
            @Param("failedAt") LocalDateTime failedAt
    );

    Optional<PhotoEvaluation> findTopByMissionPhotoIdOrderByAttemptNoDesc(
            Long missionPhotoId
    );

    @Query("""
            SELECT DISTINCT evaluation.missionPhoto.id
            FROM PhotoEvaluation evaluation
            WHERE evaluation.missionPhoto.id IN :missionPhotoIds
              AND evaluation.executionStatus =
                  com.planit.photomission.domain.PhotoEvaluationExecutionStatus.SUCCEEDED
              AND evaluation.result =
                  com.planit.photomission.domain.PhotoEvaluationResult.SUCCESS
            """)
    List<Long> findSuccessfulMissionPhotoIds(
            @Param("missionPhotoIds") List<Long> missionPhotoIds
    );

    List<PhotoEvaluation>
            findTop5ByExecutionStatusAndRetryAfterAtLessThanEqualOrderByRetryAfterAtAsc(
                    PhotoEvaluationExecutionStatus executionStatus,
                    LocalDateTime retryAfterAt
            );

    List<PhotoEvaluation>
            findTop5ByExecutionStatusAndRequestedAtLessThanEqualOrderByRequestedAtAsc(
                    PhotoEvaluationExecutionStatus executionStatus,
                    LocalDateTime requestedAt
            );
}
