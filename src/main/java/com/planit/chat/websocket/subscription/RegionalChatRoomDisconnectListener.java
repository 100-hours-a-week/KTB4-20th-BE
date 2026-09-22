package com.planit.chat.websocket.subscription;

import com.planit.chat.presence.RegionalChatRoomPresenceRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class RegionalChatRoomDisconnectListener {

    private final RegionalChatRoomPresenceRegistry presenceRegistry;

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        presenceRegistry.unregisterSession(event.getSessionId());
    }
}
