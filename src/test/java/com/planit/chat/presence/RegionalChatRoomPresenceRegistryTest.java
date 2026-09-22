package com.planit.chat.presence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegionalChatRoomPresenceRegistryTest {

    private final RegionalChatRoomPresenceRegistry registry =
            new RegionalChatRoomPresenceRegistry();

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

    @Test
    void keepsPresenceUntilLastSubscriptionInSessionIsRemoved() {
        registry.register(3001L, "user-1", "session-1", "subscription-1");
        registry.register(3001L, "user-1", "session-1", "subscription-2");

        registry.unregisterSubscription("session-1", "subscription-1");
        assertThat(registry.getActiveUserCount(3001L)).isEqualTo(1);

        registry.unregisterSubscription("session-1", "subscription-2");
        assertThat(registry.getActiveUserCount(3001L)).isZero();
    }

    @Test
    void tracksDifferentUsersSeparately() {
        registry.register(3001L, "user-1", "session-1", "subscription-1");
        registry.register(3001L, "user-2", "session-2", "subscription-2");

        assertThat(registry.getActiveUserCount(3001L)).isEqualTo(2);
    }
}
