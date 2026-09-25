package com.planit.chat.event;

import com.planit.chat.pagination.RegionalChatRoomCursorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RegionalChatRoomMembershipChangedEventListener {

    private final RegionalChatRoomCursorStore cursorStore;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void invalidateRoomListSnapshot(RegionalChatRoomMembershipChangedEvent event) {
        cursorStore.invalidateUserSnapshots(event.userPublicId());
    }
}
