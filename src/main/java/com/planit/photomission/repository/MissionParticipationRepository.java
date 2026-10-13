package com.planit.photomission.repository;

import com.planit.photomission.domain.MissionParticipation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MissionParticipationRepository
        extends JpaRepository<MissionParticipation, Long> {

    Optional<MissionParticipation> findByMissionIdAndTripMemberId(
            Long missionId,
            Long tripMemberId
    );
}
