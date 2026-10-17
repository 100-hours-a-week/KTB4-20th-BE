package com.planit.photomission.repository;

import com.planit.photomission.domain.MissionPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

    @Query("""
            SELECT photo
            FROM MissionPhoto photo
            WHERE photo.mission.id = :missionId
              AND photo.id <> :excludedPhotoId
              AND photo.deletedAt IS NULL
              AND photo.replacedAt IS NULL
            ORDER BY photo.submittedAt ASC, photo.id ASC
            """)
    List<MissionPhoto> findAvailableByMissionIdExcludingPhotoId(
            @Param("missionId") Long missionId,
            @Param("excludedPhotoId") Long excludedPhotoId
    );

    @Query("""
            SELECT DISTINCT photo
            FROM PhotoEvaluation evaluation
            JOIN evaluation.missionPhoto photo
            WHERE photo.mission.id = :missionId
              AND photo.id <> :excludedPhotoId
              AND photo.deletedAt IS NULL
              AND photo.replacedAt IS NULL
              AND evaluation.result = com.planit.photomission.domain.PhotoEvaluationResult.SUCCESS
            ORDER BY photo.submittedAt ASC, photo.id ASC
            """)
    List<MissionPhoto> findSuccessfulByMissionIdExcludingPhotoId(
            @Param("missionId") Long missionId,
            @Param("excludedPhotoId") Long excludedPhotoId
    );

    @Query("""
            SELECT photo
            FROM MissionPhoto photo
            JOIN FETCH photo.mission
            JOIN FETCH photo.submittedBy submitter
            JOIN FETCH submitter.user
            JOIN FETCH photo.imageFile
            LEFT JOIN FETCH photo.participation
            WHERE photo.mission.id IN :missionIds
              AND photo.deletedAt IS NULL
              AND photo.replacedAt IS NULL
              AND (photo.activeSlot = 1 OR photo.participation IS NULL)
            """)
    List<MissionPhoto> findVisibleByMissionIdIn(
            @Param("missionIds") List<Long> missionIds
    );
}
