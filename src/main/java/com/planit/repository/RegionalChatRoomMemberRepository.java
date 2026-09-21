package com.planit.repository;

import com.planit.domain.RegionalChatRoom;
import com.planit.domain.RegionalChatRoomMember;
import com.planit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegionalChatRoomMemberRepository
        extends JpaRepository<RegionalChatRoomMember, Long> {

    Optional<RegionalChatRoomMember> findByUserAndRegionalChatRoom(
            User user,
            RegionalChatRoom regionalChatRoom
    );
}
