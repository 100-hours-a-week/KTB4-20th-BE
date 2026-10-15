package com.planit.photomission.repository;

import com.planit.photomission.domain.MissionGenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MissionGenerationJobRepository
        extends JpaRepository<MissionGenerationJob, Long> {

    Optional<MissionGenerationJob> findByTripId(Long tripId);
}
