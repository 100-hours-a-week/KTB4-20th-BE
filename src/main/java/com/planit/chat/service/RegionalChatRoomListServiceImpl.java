package com.planit.chat.service;

import com.planit.chat.dto.RegionalChatRoomListResponse;
import com.planit.chat.dto.RegionalChatRoomListResponse.RegionalChatRoomItemResponse;
import com.planit.chat.presence.RegionalChatRoomPresenceRegistry;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.repository.RegionalChatRoomRepository;
import com.planit.repository.RegionalChatRoomRepository.RegionalChatRoomListProjection;
import com.planit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionalChatRoomListServiceImpl implements RegionalChatRoomListService {

    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private static final Comparator<RoomListEntry> ROOM_ORDER = Comparator
            .comparing(RoomListEntry::relatedToMyTrip).reversed()                       //여행 중인 지역 채팅방 1순위
            .thenComparing(RoomListEntry::joined, Comparator.reverseOrder())            //참여 중인 지역 채팅방 2순위
            .thenComparing(RoomListEntry::activeUserCount, Comparator.reverseOrder())   //접속 중인 유저 수 채팅방 3순위
            .thenComparing(RoomListEntry::memberCount, Comparator.reverseOrder())       //참여 중인 유저 수 채팅방 4순위
            .thenComparing(RoomListEntry::name)                                         //채팅방 ㄱㄴㄷ 내림차순
            .thenComparing(RoomListEntry::roomId);                                      //채팅방 id순

    private final UserRepository userRepository;
    private final RegionalChatRoomRepository regionalChatRoomRepository;
    private final RegionalChatRoomPresenceRegistry presenceRegistry;

    @Override
    public RegionalChatRoomListResponse getRegionalChatRooms(String userPublicId) {
        User user = findActiveUser(userPublicId);
        List<RegionalChatRoomItemResponse> rooms = regionalChatRoomRepository
                .findListEntries(user.getId(), LocalDate.now(SEOUL_ZONE_ID))
                .stream()
                .map(this::toEntry)
                .sorted(ROOM_ORDER)
                .map(this::toResponse)
                .toList();

        return new RegionalChatRoomListResponse(rooms);
    }

    private User findActiveUser(String userPublicId) {
        try {
            return userRepository.findByPublicIdAndDeletedAtIsNull(UUID.fromString(userPublicId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, exception);
        }
    }

    private RoomListEntry toEntry(RegionalChatRoomListProjection projection) {
        return new RoomListEntry(
                projection.getRoomId(),
                projection.getRegionId(),
                projection.getName(),
                projection.getMemberCount(),
                presenceRegistry.getActiveUserCount(projection.getRoomId()),
                Boolean.TRUE.equals(projection.getRelatedToMyTrip()),
                Boolean.TRUE.equals(projection.getJoined()),
                Boolean.TRUE.equals(projection.getCanJoin())
        );
    }

    private RegionalChatRoomItemResponse toResponse(RoomListEntry room) {
        return new RegionalChatRoomItemResponse(
                room.roomId().toString(), room.regionId().toString(), room.name(),
                room.memberCount(), room.activeUserCount(), room.relatedToMyTrip(),
                room.joined(), room.canJoin()
        );
    }

    private record RoomListEntry(
            Long roomId,
            Long regionId,
            String name,
            Long memberCount,
            Long activeUserCount,
            boolean relatedToMyTrip,
            boolean joined,
            boolean canJoin
    ) {
    }
}
