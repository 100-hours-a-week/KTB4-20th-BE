package com.planit.photomission.repository;

import com.planit.photomission.domain.MissionGroupState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MissionGroupStateRepository
        extends JpaRepository<MissionGroupState, Long> {

    Optional<MissionGroupState> findByMissionId(Long missionId);
}
