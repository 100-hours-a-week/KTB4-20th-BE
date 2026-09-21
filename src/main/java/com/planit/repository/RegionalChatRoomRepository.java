package com.planit.repository;

import com.planit.domain.RegionalChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RegionalChatRoomRepository extends JpaRepository<RegionalChatRoom, Long> {

    Optional<RegionalChatRoom> findByRegion_Id(Long regionId);

    @Query(value = """
            SELECT room.id AS roomId,
                   room.region_id AS regionId,
                   room.name AS name,
                   EXISTS (
                       SELECT 1
                       FROM trip_members trip_member
                       JOIN trips trip ON trip.id = trip_member.trip_id
                       WHERE trip_member.user_id = :userId
                         AND trip_member.left_at IS NULL
                         AND trip.deleted_at IS NULL
                         AND trip.sub_region_id = room.region_id
                         AND :today BETWEEN trip.start_date AND trip.end_date
                   ) AS relatedToMyTrip,
                   EXISTS (
                       SELECT 1
                       FROM regional_chat_room_members my_membership
                       WHERE my_membership.user_id = :userId
                         AND my_membership.regional_chat_room_id = room.id
                         AND my_membership.left_at IS NULL
                   ) AS joined,
                   (
                       SELECT COUNT(DISTINCT membership.user_id)
                       FROM regional_chat_room_members membership
                       WHERE membership.regional_chat_room_id = room.id
                         AND membership.left_at IS NULL
                   ) AS memberCount,
                   EXISTS (
                       SELECT 1
                       FROM chat_policy_versions policy
                       JOIN chat_policy_consents consent
                         ON consent.chat_policy_version_id = policy.id
                       WHERE policy.status = 'ACTIVE'
                         AND consent.user_id = :userId
                   ) AS canJoin
            FROM regional_chat_rooms room
            """, nativeQuery = true)
    List<RegionalChatRoomListProjection> findListEntries(
            @Param("userId") Long userId,
            @Param("today") LocalDate today
    );

    interface RegionalChatRoomListProjection {
        Long getRoomId();

        Long getRegionId();

        String getName();

        Boolean getRelatedToMyTrip();

        Boolean getJoined();

        Long getMemberCount();

        Boolean getCanJoin();
    }
}
