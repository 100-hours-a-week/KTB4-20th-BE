package com.planit.repository;

import com.planit.domain.TripMember;
import com.planit.domain.Trip;
import com.planit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface TripMemberRepository
        extends JpaRepository<TripMember, Long> {

    boolean existsByTripAndUserAndLeftAtIsNull(
            Trip trip,
            User user
    );

    long countByTripAndLeftAtIsNull(Trip trip);

    @Query("""
            SELECT COUNT(tm)
            FROM TripMember tm
            WHERE tm.user = :user
              AND tm.activeSlot = 1
              AND tm.leftAt IS NULL
              AND tm.trip.deletedAt IS NULL
              AND tm.trip.startDate <= :endDate
              AND tm.trip.endDate >= :startDate
            """)
    long countActiveTripsOverlapping(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
