package com.planit.chat.service;

import com.planit.chat.dto.RegionalChatRoomListResponse;
import com.planit.chat.pagination.RegionalChatRoomCursorStore;
import com.planit.chat.pagination.RegionalChatRoomCursorStore.CursorPage;
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
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegionalChatRoomListServiceImplTest {

    private static final String USER_PUBLIC_ID = "01991f6e-7300-7b21-a3cc-1436db3df95e";

    private UserRepository userRepository;
    private RegionalChatRoomRepository roomRepository;
    private RegionalChatRoomPresenceRegistry presenceRegistry;
    private RegionalChatRoomCursorStore cursorStore;
    private RegionalChatRoomListServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roomRepository = mock(RegionalChatRoomRepository.class);
        presenceRegistry = mock(RegionalChatRoomPresenceRegistry.class);
        cursorStore = mock(RegionalChatRoomCursorStore.class);
        service = new RegionalChatRoomListServiceImpl(
                userRepository, roomRepository, presenceRegistry, cursorStore
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

        RegionalChatRoomListResponse response = service
                .getRegionalChatRooms(USER_PUBLIC_ID, null);

        assertThat(response.items())
                .extracting(RegionalChatRoomListResponse.RegionalChatRoomItemResponse::roomId)
                .containsExactly("5", "1", "2", "3", "4", "6", "7");
        assertThat(response.page().hasNext()).isFalse();
        assertThat(response.page().nextCursor()).isNull();
    }

    @Test
    void returnsTwentyRoomsAndIssuesCursorOnInitialRequest() {
        List<RegionalChatRoomListProjection> rooms = LongStream.rangeClosed(1, 21)
                .mapToObj(id -> projection(id, "지역 " + id, false, false, 0, false))
                .toList();
        when(roomRepository.findListEntries(eq(77L), any(LocalDate.class)))
                .thenReturn(rooms);
        when(cursorStore.createSnapshot(eq(USER_PUBLIC_ID), any(), eq(20)))
                .thenReturn("next-cursor");

        RegionalChatRoomListResponse response = service
                .getRegionalChatRooms(USER_PUBLIC_ID, null);

        assertThat(response.items()).hasSize(20);
        assertThat(response.page().hasNext()).isTrue();
        assertThat(response.page().nextCursor()).isEqualTo("next-cursor");
        verify(cursorStore).invalidateUserSnapshots(USER_PUBLIC_ID);
    }

    @Test
    void returnsTenRoomsAfterCursor() {
        List<RegionalChatRoomListProjection> rooms = LongStream.rangeClosed(1, 22)
                .mapToObj(id -> projection(
                        id, "지역 %02d".formatted(id), false, false, 0, false
                ))
                .toList();
        when(roomRepository.findListEntries(eq(77L), any(LocalDate.class)))
                .thenReturn(rooms);
        when(cursorStore.resolve("cursor", USER_PUBLIC_ID, 10))
                .thenReturn(new CursorPage(
                        LongStream.rangeClosed(6, 15).boxed().toList(),
                        "next-cursor",
                        true
                ));

        RegionalChatRoomListResponse response = service
                .getRegionalChatRooms(USER_PUBLIC_ID, "cursor");

        assertThat(response.items()).hasSize(10);
        assertThat(response.items().getFirst().roomId()).isEqualTo("6");
        assertThat(response.items().getLast().roomId()).isEqualTo("15");
        assertThat(response.page().hasNext()).isTrue();
        assertThat(response.page().nextCursor()).isEqualTo("next-cursor");
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
