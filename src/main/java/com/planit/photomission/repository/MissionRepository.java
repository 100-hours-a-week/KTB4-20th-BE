package com.planit.photomission.repository;

import com.planit.photomission.domain.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MissionRepository extends JpaRepository<Mission, Long> {

    @Query("""
            SELECT mission
            FROM Mission mission
            JOIN FETCH mission.scheduleDay scheduleDay
            JOIN FETCH mission.scheduleVisit scheduleVisit
            JOIN FETCH scheduleVisit.place
            WHERE mission.trip.id = :tripId
              AND scheduleDay.dayNumber = :dayNumber
            ORDER BY mission.missionOrder ASC, mission.id ASC
            """)
    List<Mission> findByTripIdAndScheduleDayDayNumberOrderByMissionOrderAsc(
            @Param("tripId") Long tripId,
            @Param("dayNumber") byte dayNumber
    );

    @Query("""
            SELECT mission
            FROM Mission mission
            JOIN FETCH mission.scheduleDay scheduleDay
            JOIN FETCH mission.scheduleVisit scheduleVisit
            JOIN FETCH scheduleVisit.place
            WHERE mission.trip.id = :tripId
            ORDER BY scheduleDay.dayNumber ASC,
                     mission.missionOrder ASC,
                     mission.id ASC
            """)
    List<Mission> findByTripIdOrderByDayAndMissionOrder(
            @Param("tripId") Long tripId
    );
}
