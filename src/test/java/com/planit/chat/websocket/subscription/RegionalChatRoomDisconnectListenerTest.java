package com.planit.chat.websocket.subscription;

import com.planit.chat.presence.RegionalChatRoomPresenceRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegionalChatRoomDisconnectListenerTest {

    @DisplayName("연결이 종료된 세션의 모든 구독을 해제한다")
    @Test
    void unregistersEverySubscriptionForDisconnectedSession() {
        RegionalChatRoomPresenceRegistry presenceRegistry =
                mock(RegionalChatRoomPresenceRegistry.class);
        RegionalChatRoomDisconnectListener listener =
                new RegionalChatRoomDisconnectListener(presenceRegistry);
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getSessionId()).thenReturn("session-1");

        listener.handleDisconnect(event);

        verify(presenceRegistry).unregisterSession("session-1");
    }
}
