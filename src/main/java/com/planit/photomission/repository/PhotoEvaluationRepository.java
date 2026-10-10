package com.planit.photomission.repository;

import com.planit.photomission.domain.PhotoEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PhotoEvaluationRepository
        extends JpaRepository<PhotoEvaluation, Long> {

    Optional<PhotoEvaluation> findTopByMissionPhotoIdOrderByAttemptNoDesc(
            Long missionPhotoId
    );
}
