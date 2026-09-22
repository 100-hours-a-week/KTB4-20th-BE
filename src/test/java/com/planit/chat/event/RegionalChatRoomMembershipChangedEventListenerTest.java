package com.planit.chat.event;

import com.planit.chat.pagination.RegionalChatRoomCursorStore;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RegionalChatRoomMembershipChangedEventListenerTest {

    @Test
    void invalidatesUserSnapshotsAfterMembershipChange() {
        RegionalChatRoomCursorStore cursorStore = mock(RegionalChatRoomCursorStore.class);
        RegionalChatRoomMembershipChangedEventListener listener =
                new RegionalChatRoomMembershipChangedEventListener(cursorStore);

        listener.invalidateRoomListSnapshot(
                new RegionalChatRoomMembershipChangedEvent("user-public-id")
        );

        verify(cursorStore).invalidateUserSnapshots("user-public-id");
    }
}
