package com.planit.chat.service;

import com.planit.chat.dto.RegionalChatRoomListResponse;
import com.planit.chat.dto.RegionalChatRoomListResponse.CursorPageResponse;
import com.planit.chat.dto.RegionalChatRoomListResponse.RegionalChatRoomItemResponse;
import com.planit.chat.pagination.RegionalChatRoomCursorStore;
import com.planit.chat.pagination.RegionalChatRoomCursorStore.CursorPage;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegionalChatRoomListServiceImpl implements RegionalChatRoomListService {

    private static final int INITIAL_PAGE_SIZE = 20;
    private static final int NEXT_PAGE_SIZE = 10;
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
    private final RegionalChatRoomCursorStore cursorStore;

    @Override
    public RegionalChatRoomListResponse getRegionalChatRooms(
            String userPublicId,
            String cursor
    ) {
        User user = findActiveUser(userPublicId);
        List<RoomListEntry> currentRooms = regionalChatRoomRepository
                .findListEntries(user.getId(), LocalDate.now(SEOUL_ZONE_ID))
                .stream()
                .map(this::toEntry)
                .toList();

        if (cursor == null) {
            return firstPage(userPublicId, currentRooms);
        }
        return nextPage(userPublicId, cursor, currentRooms);
    }

    private RegionalChatRoomListResponse firstPage(
            String userPublicId,
            List<RoomListEntry> currentRooms
    ) {
        cursorStore.invalidateUserSnapshots(userPublicId);
        List<RoomListEntry> orderedRooms = currentRooms.stream().sorted(ROOM_ORDER).toList();
        boolean hasNext = orderedRooms.size() > INITIAL_PAGE_SIZE;
        List<RoomListEntry> pageRooms = orderedRooms.stream()
                .limit(INITIAL_PAGE_SIZE)
                .toList();
        String nextCursor = hasNext
                ? cursorStore.createSnapshot(
                        userPublicId,
                        orderedRooms.stream().map(RoomListEntry::roomId).toList(),
                        INITIAL_PAGE_SIZE
                )
                : null;

        return toListResponse(pageRooms, nextCursor, hasNext);
    }

    private RegionalChatRoomListResponse nextPage(
            String userPublicId,
            String cursor,
            List<RoomListEntry> currentRooms
    ) {
        CursorPage cursorPage = cursorStore.resolve(cursor, userPublicId, NEXT_PAGE_SIZE);
        Map<Long, RoomListEntry> roomsById = currentRooms.stream()
                .collect(Collectors.toMap(RoomListEntry::roomId, Function.identity()));
        List<RoomListEntry> pageRooms = cursorPage.roomIds().stream()
                .map(roomsById::get)
                .filter(Objects::nonNull)
                .toList();

        return toListResponse(pageRooms, cursorPage.nextCursor(), cursorPage.hasNext());
    }

    private RegionalChatRoomListResponse toListResponse(
            List<RoomListEntry> pageRooms,
            String nextCursor,
            boolean hasNext
    ) {

        return new RegionalChatRoomListResponse(
                pageRooms.stream().map(this::toResponse).toList(),
                new CursorPageResponse(nextCursor, hasNext)
        );
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
