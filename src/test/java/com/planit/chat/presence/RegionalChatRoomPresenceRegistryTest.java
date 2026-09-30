package com.planit.chat.presence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegionalChatRoomPresenceRegistryTest {

    private final RegionalChatRoomPresenceRegistry registry =
            new RegionalChatRoomPresenceRegistry();

    @DisplayName("여러 세션에 접속한 동일 사용자를 한 명으로 집계한다")
    @Test
    void countsUserOnceAcrossMultipleSessions() {
        registry.register(3001L, "user-1", "session-1", "subscription-1");
        registry.register(3001L, "user-1", "session-2", "subscription-2");

        assertThat(registry.getActiveUserCount(3001L)).isEqualTo(1);

        registry.unregisterSession("session-1");
        assertThat(registry.getActiveUserCount(3001L)).isEqualTo(1);

        registry.unregisterSession("session-2");
        assertThat(registry.getActiveUserCount(3001L)).isZero();
    }

    @DisplayName("세션의 마지막 구독이 해제될 때까지 접속 상태를 유지한다")
    @Test
    void keepsPresenceUntilLastSubscriptionInSessionIsRemoved() {
        registry.register(3001L, "user-1", "session-1", "subscription-1");
        registry.register(3001L, "user-1", "session-1", "subscription-2");

        registry.unregisterSubscription("session-1", "subscription-1");
        assertThat(registry.getActiveUserCount(3001L)).isEqualTo(1);

        registry.unregisterSubscription("session-1", "subscription-2");
        assertThat(registry.getActiveUserCount(3001L)).isZero();
    }

    @DisplayName("서로 다른 사용자의 접속 상태를 개별 관리한다")
    @Test
    void tracksDifferentUsersSeparately() {
        registry.register(3001L, "user-1", "session-1", "subscription-1");
        registry.register(3001L, "user-2", "session-2", "subscription-2");

        assertThat(registry.getActiveUserCount(3001L)).isEqualTo(2);
    }
}
