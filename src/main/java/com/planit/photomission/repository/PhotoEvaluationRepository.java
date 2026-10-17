package com.planit.photomission.repository;

import com.planit.photomission.domain.PhotoEvaluation;
import com.planit.photomission.domain.PhotoEvaluationExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PhotoEvaluationRepository
        extends JpaRepository<PhotoEvaluation, Long> {

    Optional<PhotoEvaluation> findTopByMissionPhotoIdOrderByAttemptNoDesc(
            Long missionPhotoId
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
