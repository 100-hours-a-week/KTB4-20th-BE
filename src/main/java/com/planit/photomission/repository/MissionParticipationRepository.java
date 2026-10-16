package com.planit.photomission.repository;

import com.planit.photomission.domain.MissionParticipation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MissionParticipationRepository
        extends JpaRepository<MissionParticipation, Long> {

    Optional<MissionParticipation> findByMissionIdAndTripMemberId(
            Long missionId,
            Long tripMemberId
    );

    @Query("""
            SELECT participation
            FROM MissionParticipation participation
            JOIN FETCH participation.mission
            JOIN FETCH participation.tripMember member
            WHERE participation.mission.id IN :missionIds
            """)
    List<MissionParticipation> findByMissionIdIn(
            @Param("missionIds") List<Long> missionIds
    );
}
