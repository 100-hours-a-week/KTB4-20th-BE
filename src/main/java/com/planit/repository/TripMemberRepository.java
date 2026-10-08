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

    /** 회원 탈퇴 시, 이 사용자가 활성 멤버로 남아있는 모든 여행방을 정리하기 위해 씁니다. */
    List<TripMember> findByUserAndActiveSlotAndLeftAtIsNull(
            User user,
            Byte activeSlot
    );

    Optional<TripMember> findByTripAndUserAndLeftAtIsNull(
            Trip trip,
            User user
    );

    @Query("""
            SELECT tm
            FROM TripMember tm
            JOIN FETCH tm.user memberUser
            WHERE tm.trip = :trip
              AND tm.activeSlot = 1
              AND tm.leftAt IS NULL
              AND memberUser.deletedAt IS NULL
            ORDER BY tm.joinedAt ASC, tm.id ASC
            """)
    List<TripMember> findActiveMembersByTrip(
            @Param("trip") Trip trip
    );

    /**
     * 여행방 상세 화면처럼, 탈퇴한 사용자의 멤버십도 "탈퇴한 사용자"로 표시하기 위해 남겨둬야
     * 하는 화면에서 쓴다. findActiveMembersByTrip과 달리 memberUser.deletedAt을 걸러내지
     * 않는다.
     */
    @Query("""
            SELECT tm
            FROM TripMember tm
            JOIN FETCH tm.user memberUser
            WHERE tm.trip = :trip
              AND tm.activeSlot = 1
              AND tm.leftAt IS NULL
            ORDER BY tm.joinedAt ASC, tm.id ASC
            """)
    List<TripMember> findActiveMembersByTripIncludingWithdrawn(
            @Param("trip") Trip trip
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
            SELECT tm
            FROM TripMember tm
            JOIN FETCH tm.trip trip
            WHERE tm.user = :user
              AND tm.activeSlot = 1
              AND tm.leftAt IS NULL
              AND trip.deletedAt IS NULL
              AND trip.startDate <= :endDate
              AND trip.endDate >= :startDate
            ORDER BY trip.startDate, trip.id
            """)
    List<TripMember> findActiveTripsOverlapping(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT tm
            FROM TripMember tm
            JOIN FETCH tm.trip trip
            WHERE tm.user = :user
              AND tm.activeSlot = 1
              AND tm.leftAt IS NULL
              AND trip.deletedAt IS NULL
              AND trip.id <> :excludedTripId
              AND trip.startDate <= :endDate
              AND trip.endDate >= :startDate
            ORDER BY trip.startDate, trip.id
            """)
    List<TripMember> findActiveTripsOverlappingExcept(
            @Param("user") User user,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("excludedTripId") Long excludedTripId
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
              CASE
                WHEN trip.startDate <= :referenceDate
                  AND trip.endDate >= :referenceDate THEN 0
                WHEN trip.startDate > :referenceDate THEN 1
                ELSE 2
              END,
              CASE WHEN trip.endDate >= :referenceDate THEN trip.startDate END ASC,
              CASE WHEN trip.endDate < :referenceDate THEN trip.endDate END DESC,
              trip.id ASC
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
                CASE
                  WHEN trip.startDate <= :referenceDate
                    AND trip.endDate >= :referenceDate THEN 0
                  WHEN trip.startDate > :referenceDate THEN 1
                  ELSE 2
                END > :cursorSectionOrder
                OR (
                  CASE
                    WHEN trip.startDate <= :referenceDate
                      AND trip.endDate >= :referenceDate THEN 0
                    WHEN trip.startDate > :referenceDate THEN 1
                    ELSE 2
                  END = :cursorSectionOrder
                  AND (
                    (
                      :cursorSectionOrder = 2
                      AND (
                        trip.endDate < :cursorSortDate
                        OR (
                          trip.endDate = :cursorSortDate
                          AND trip.id > :cursorTripId
                        )
                      )
                    )
                    OR (
                      :cursorSectionOrder <> 2
                      AND (
                        trip.startDate > :cursorSortDate
                        OR (
                          trip.startDate = :cursorSortDate
                          AND trip.id > :cursorTripId
                        )
                      )
                    )
                  )
                )
              )
            ORDER BY
              CASE
                WHEN trip.startDate <= :referenceDate
                  AND trip.endDate >= :referenceDate THEN 0
                WHEN trip.startDate > :referenceDate THEN 1
                ELSE 2
              END,
              CASE WHEN trip.endDate >= :referenceDate THEN trip.startDate END ASC,
              CASE WHEN trip.endDate < :referenceDate THEN trip.endDate END DESC,
              trip.id ASC
            """)
    List<TripMember> findActiveTripMembershipsAfter(
            @Param("user") User user,
            @Param("referenceDate") LocalDate referenceDate,
            @Param("cursorSectionOrder") int cursorSectionOrder,
            @Param("cursorSortDate") LocalDate cursorSortDate,
            @Param("cursorTripId") Long cursorTripId,
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
