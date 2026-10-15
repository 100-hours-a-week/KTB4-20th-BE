package com.planit.photomission.repository;

import com.planit.photomission.domain.MissionGenerationJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
