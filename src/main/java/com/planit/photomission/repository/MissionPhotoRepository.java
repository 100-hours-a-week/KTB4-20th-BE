package com.planit.photomission.repository;

import com.planit.photomission.domain.MissionPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MissionPhotoRepository
        extends JpaRepository<MissionPhoto, Long> {

    Optional<MissionPhoto> findByIdempotencyKey(UUID idempotencyKey);

    @Query("""
            SELECT photo
            FROM MissionPhoto photo
            WHERE photo.participation.id = :participationId
              AND photo.activeSlot = 1
              AND photo.deletedAt IS NULL
            """)
    Optional<MissionPhoto> findActiveByParticipationId(
            @Param("participationId") Long participationId
    );

    @Query("""
            SELECT photo
            FROM MissionPhoto photo
            WHERE photo.mission.id = :missionId
              AND photo.representativeSlot = 1
              AND photo.deletedAt IS NULL
            """)
    Optional<MissionPhoto> findRepresentativeByMissionId(
            @Param("missionId") Long missionId
    );
}
