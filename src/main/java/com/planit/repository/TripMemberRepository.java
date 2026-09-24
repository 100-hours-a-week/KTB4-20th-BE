package com.planit.repository;

import com.planit.domain.TripMember;
import com.planit.domain.Trip;
import com.planit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TripMemberRepository
        extends JpaRepository<TripMember, Long> {

    boolean existsByTripAndUserAndLeftAtIsNull(
            Trip trip,
            User user
    );

    long countByTripAndLeftAtIsNull(Trip trip);

    Optional<TripMember> findByTripAndUserAndLeftAtIsNull(
            Trip trip,
            User user
    );

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

    @Query("""
            SELECT member
            FROM TripMember member
            JOIN FETCH member.trip trip
            WHERE member.user = :user
              AND member.activeSlot = 1
              AND member.leftAt IS NULL
              AND trip.deletedAt IS NULL
            ORDER BY
              CASE WHEN trip.startDate >= :referenceDate THEN 0 ELSE 1 END,
              trip.startDate
            """)
    List<TripMember> findActiveTripMemberships(
            @Param("user") User user,
            @Param("referenceDate") LocalDate referenceDate,
            Pageable pageable
    );

    @Query("""
            SELECT member
            FROM TripMember member
            JOIN FETCH member.trip trip
            WHERE member.user = :user
              AND member.activeSlot = 1
              AND member.leftAt IS NULL
              AND trip.deletedAt IS NULL
              AND (
                CASE WHEN trip.startDate >= :referenceDate THEN 0 ELSE 1 END
                    > :cursorSectionOrder
                OR (
                  CASE WHEN trip.startDate >= :referenceDate THEN 0 ELSE 1 END
                      = :cursorSectionOrder
                  AND trip.startDate > :cursorStartDate
                )
              )
            ORDER BY
              CASE WHEN trip.startDate >= :referenceDate THEN 0 ELSE 1 END,
              trip.startDate
            """)
    List<TripMember> findActiveTripMembershipsAfter(
            @Param("user") User user,
            @Param("referenceDate") LocalDate referenceDate,
            @Param("cursorSectionOrder") int cursorSectionOrder,
            @Param("cursorStartDate") LocalDate cursorStartDate,
            Pageable pageable
    );

    @Query("""
            SELECT member
            FROM TripMember member
            JOIN FETCH member.trip trip
            JOIN FETCH member.user user
            WHERE trip.id IN :tripIds
              AND member.activeSlot = 1
              AND member.leftAt IS NULL
              AND user.deletedAt IS NULL
            ORDER BY trip.id, member.joinedAt, member.id
            """)
    List<TripMember> findActiveMembersByTripIds(
            @Param("tripIds") List<Long> tripIds
    );
}
