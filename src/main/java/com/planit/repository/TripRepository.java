package com.planit.repository;

import com.planit.domain.Trip;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TripRepository
        extends JpaRepository<Trip, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT trip FROM Trip trip WHERE trip.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            SELECT DISTINCT s.trip_id
            FROM schedules s
            WHERE s.trip_id IN (:tripIds)
              AND s.active_confirmed_slot = 1
            """, nativeQuery = true)
    List<Long> findIdsWithActiveConfirmedSchedule(
            @Param("tripIds") List<Long> tripIds
    );

    @Query(value = """
            SELECT DISTINCT trip.id
            FROM trips trip
            JOIN schedules schedule
              ON schedule.trip_id = trip.id
             AND schedule.active_confirmed_slot = 1
            LEFT JOIN mission_generation_jobs job
              ON job.trip_id = trip.id
            WHERE trip.deleted_at IS NULL
              AND trip.start_date = :today
              AND (
                job.id IS NULL
                OR job.status = 'FAILED'
                OR (
                  job.status = 'RUNNING'
                  AND job.last_attempted_at <= :staleBefore
                )
              )
            ORDER BY trip.id
            """, nativeQuery = true)
    List<Long> findPhotoMissionGenerationTargetIds(
            @Param("today") LocalDate today,
            @Param("staleBefore") LocalDateTime staleBefore
    );
}
