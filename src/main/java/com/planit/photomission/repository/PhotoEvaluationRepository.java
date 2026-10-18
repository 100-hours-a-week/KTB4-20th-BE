package com.planit.photomission.repository;

import com.planit.photomission.domain.PhotoEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PhotoEvaluationRepository
        extends JpaRepository<PhotoEvaluation, Long> {

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

    @Query("""
            SELECT evaluation
            FROM PhotoEvaluation evaluation
            JOIN FETCH evaluation.missionPhoto photo
            WHERE photo.id IN :missionPhotoIds
              AND evaluation.executionStatus =
                  com.planit.photomission.domain.PhotoEvaluationExecutionStatus.SUCCEEDED
              AND evaluation.result =
                  com.planit.photomission.domain.PhotoEvaluationResult.SUCCESS
              AND evaluation.landmarkConfidence IS NOT NULL
            """)
    List<PhotoEvaluation> findRecognizedByMissionPhotoIdIn(
            @Param("missionPhotoIds") List<Long> missionPhotoIds
    );
}
