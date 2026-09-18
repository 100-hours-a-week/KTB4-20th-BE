package com.planit.repository;

import com.planit.domain.RegionalChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegionalChatRoomRepository extends JpaRepository<RegionalChatRoom, Long> {

    Optional<RegionalChatRoom> findByRegion_Id(Long regionId);
}
