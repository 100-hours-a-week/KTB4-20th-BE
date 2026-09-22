package com.planit.chat.presence;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RegionalChatRoomPresenceRegistry {

    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Set<String>>> sessionsByRoomAndUser
            = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Subscription>> subscriptionsBySession
            = new ConcurrentHashMap<>();

    public long getActiveUserCount(Long roomId) {
        ConcurrentHashMap<String, Set<String>> sessionsByUser = sessionsByRoomAndUser.get(roomId);
        return sessionsByUser == null ? 0 : sessionsByUser.size();
    }

    public void register(
            Long roomId,
            String userPublicId,
            String sessionId,
            String subscriptionId
    ) {
        Subscription subscription = new Subscription(roomId, userPublicId);
        Subscription previous = subscriptionsBySession
                .computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>())   //채팅방 구독자가 없으면 생성 후 사용자를 추가
                .put(subscriptionId, subscription);

        if (previous != null && !previous.equals(subscription)) {   //동일한 subscriptionId에 기존 구독이 존재했고, 기존 구독과 새 구독 대상이 다르면 기존 접속 상태를 정리
            removePresenceIfUnused(sessionId, previous);
        }
        sessionsByRoomAndUser
                .computeIfAbsent(roomId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(userPublicId, ignored -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
    }

    public void unregisterSubscription(String sessionId, String subscriptionId) {
        if (sessionId == null || subscriptionId == null) {
            return;
        }
        ConcurrentHashMap<String, Subscription> subscriptions = subscriptionsBySession.get(sessionId);
        if (subscriptions == null) {
            return;
        }

        Subscription removed = subscriptions.remove(subscriptionId);
        if (subscriptions.isEmpty()) {
            subscriptionsBySession.remove(sessionId, subscriptions);
        }
        if (removed != null) {
            removePresenceIfUnused(sessionId, removed);
        }
    }

    public void unregisterSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        Map<String, Subscription> removedSubscriptions = subscriptionsBySession.remove(sessionId);
        if (removedSubscriptions == null) {
            return;
        }

        removedSubscriptions.values().stream()
                .distinct()
                .forEach(subscription -> removePresence(sessionId, subscription));
    }

    private void removePresenceIfUnused(String sessionId, Subscription subscription) {
        Map<String, Subscription> remaining = subscriptionsBySession.get(sessionId);
        if (remaining != null && remaining.containsValue(subscription)) {
            return;
        }
        removePresence(sessionId, subscription);
    }

    private void removePresence(String sessionId, Subscription subscription) {
        ConcurrentHashMap<String, Set<String>> sessionsByUser = sessionsByRoomAndUser
                .get(subscription.roomId());
        if (sessionsByUser == null) {
            return;
        }

        sessionsByUser.computeIfPresent(subscription.userPublicId(), (ignored, sessionIds) -> {
            sessionIds.remove(sessionId);
            return sessionIds.isEmpty() ? null : sessionIds;
        });
        if (sessionsByUser.isEmpty()) {
            sessionsByRoomAndUser.remove(subscription.roomId(), sessionsByUser);
        }
    }

    private record Subscription(Long roomId, String userPublicId) {
    }
}
