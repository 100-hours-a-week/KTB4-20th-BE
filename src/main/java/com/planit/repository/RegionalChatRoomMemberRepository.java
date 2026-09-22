package com.planit.repository;

import com.planit.domain.RegionalChatRoom;
import com.planit.domain.RegionalChatRoomMember;
import com.planit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

import java.util.Optional;

public interface RegionalChatRoomMemberRepository
        extends JpaRepository<RegionalChatRoomMember, Long> {

    Optional<RegionalChatRoomMember> findByUserAndRegionalChatRoom(
            User user,
            RegionalChatRoom regionalChatRoom
    );

    @Query("""
            SELECT CASE WHEN COUNT(member) > 0 THEN true ELSE false END
            FROM RegionalChatRoomMember member
            WHERE member.user.publicId = :userPublicId
              AND member.user.deletedAt IS NULL
              AND member.regionalChatRoom.id = :roomId
              AND member.leftAt IS NULL
            """)
    boolean existsActiveMembership(
            @Param("userPublicId") UUID userPublicId,
            @Param("roomId") Long roomId
    );
}
