package com.planit.chat.presence;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RegionalChatRoomPresenceRegistry {

    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Set<String>>> sessionsByRoomAndUser
            = new ConcurrentHashMap<>();

    public long getActiveUserCount(Long roomId) {
        ConcurrentHashMap<String, Set<String>> sessionsByUser = sessionsByRoomAndUser.get(roomId);
        return sessionsByUser == null ? 0 : sessionsByUser.size();
    }

    public void register(Long roomId, String userPublicId, String sessionId) {
        sessionsByRoomAndUser
                .computeIfAbsent(roomId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(userPublicId, ignored -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
    }

    public void unregister(Long roomId, String userPublicId, String sessionId) {
        ConcurrentHashMap<String, Set<String>> sessionsByUser = sessionsByRoomAndUser.get(roomId);
        if (sessionsByUser == null) {
            return;
        }

        sessionsByUser.computeIfPresent(userPublicId, (ignored, sessionIds) -> {
            sessionIds.remove(sessionId);
            return sessionIds.isEmpty() ? null : sessionIds;
        });
        if (sessionsByUser.isEmpty()) {
            sessionsByRoomAndUser.remove(roomId, sessionsByUser);
        }
    }
}
