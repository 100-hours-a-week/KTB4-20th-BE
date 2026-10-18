package com.planit.photomission.repository;

import com.planit.photomission.domain.MissionGroupState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MissionGroupStateRepository
        extends JpaRepository<MissionGroupState, Long> {

    Optional<MissionGroupState> findByMissionId(Long missionId);

    @Query("""
            SELECT state
            FROM MissionGroupState state
            JOIN FETCH state.mission
            WHERE state.mission.id IN :missionIds
            """)
    List<MissionGroupState> findByMissionIdIn(
            @Param("missionIds") List<Long> missionIds
    );
}
