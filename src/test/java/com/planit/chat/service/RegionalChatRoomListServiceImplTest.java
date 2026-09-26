package com.planit.chat.service;

import com.planit.chat.dto.RegionalChatRoomListResponse;
import com.planit.chat.presence.RegionalChatRoomPresenceRegistry;
import com.planit.domain.User;
import com.planit.repository.RegionalChatRoomRepository;
import com.planit.repository.RegionalChatRoomRepository.RegionalChatRoomListProjection;
import com.planit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegionalChatRoomListServiceImplTest {

    private static final String USER_PUBLIC_ID = "01991f6e-7300-7b21-a3cc-1436db3df95e";

    private UserRepository userRepository;
    private RegionalChatRoomRepository roomRepository;
    private RegionalChatRoomPresenceRegistry presenceRegistry;
    private RegionalChatRoomListServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roomRepository = mock(RegionalChatRoomRepository.class);
        presenceRegistry = mock(RegionalChatRoomPresenceRegistry.class);
        service = new RegionalChatRoomListServiceImpl(
                userRepository, roomRepository, presenceRegistry
        );

        User user = mock(User.class);
        when(user.getId()).thenReturn(77L);
        when(userRepository.findByPublicIdAndDeletedAtIsNull(UUID.fromString(USER_PUBLIC_ID)))
                .thenReturn(Optional.of(user));
    }

    @Test
    void sortsRoomsByApprovedContractOrder() {
        List<RegionalChatRoomListProjection> rooms = List.of(
                projection(7, "가 지역", false, true, 5, true),
                projection(6, "가 지역", true, false, 100, true),
                projection(5, "라 지역", true, true, 10, true),
                projection(4, "다 지역", true, true, 20, true),
                projection(3, "나 지역", true, true, 20, true),
                projection(2, "가 지역", true, true, 20, true),
                projection(1, "가 지역", true, true, 20, true)
        );
        when(roomRepository.findListEntries(eq(77L), any(LocalDate.class)))
                .thenReturn(rooms);
        when(presenceRegistry.getActiveUserCount(5L)).thenReturn(2L);
        when(presenceRegistry.getActiveUserCount(4L)).thenReturn(1L);
        when(presenceRegistry.getActiveUserCount(3L)).thenReturn(1L);
        when(presenceRegistry.getActiveUserCount(2L)).thenReturn(1L);
        when(presenceRegistry.getActiveUserCount(1L)).thenReturn(1L);

        RegionalChatRoomListResponse response = service.getRegionalChatRooms(USER_PUBLIC_ID);

        assertThat(response.items())
                .extracting(RegionalChatRoomListResponse.RegionalChatRoomItemResponse::roomId)
                .containsExactly("5", "1", "2", "3", "4", "6", "7");
    }

    @Test
    void returnsEveryRoomInOneResponse() {
        List<RegionalChatRoomListProjection> rooms = List.of(
                projection(1, "서울", false, false, 0, false),
                projection(2, "경주", false, false, 0, false),
                projection(3, "부산", false, false, 0, false),
                projection(4, "전주", false, false, 0, false),
                projection(5, "제주", false, false, 0, false)
        );
        when(roomRepository.findListEntries(eq(77L), any(LocalDate.class)))
                .thenReturn(rooms);

        RegionalChatRoomListResponse response = service.getRegionalChatRooms(USER_PUBLIC_ID);

        assertThat(response.items()).hasSize(5);
    }

    private RegionalChatRoomListProjection projection(
            long id,
            String name,
            boolean relatedToMyTrip,
            boolean joined,
            long memberCount,
            boolean canJoin
    ) {
        RegionalChatRoomListProjection projection = mock(RegionalChatRoomListProjection.class);
        when(projection.getRoomId()).thenReturn(id);
        when(projection.getRegionId()).thenReturn(id + 100);
        when(projection.getName()).thenReturn(name);
        when(projection.getRelatedToMyTrip()).thenReturn(relatedToMyTrip);
        when(projection.getJoined()).thenReturn(joined);
        when(projection.getMemberCount()).thenReturn(memberCount);
        when(projection.getCanJoin()).thenReturn(canJoin);
        return projection;
    }
}
