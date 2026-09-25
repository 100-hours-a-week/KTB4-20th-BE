package com.planit.chat.pagination;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMessageCursorStoreTest {

    private static final String USER_PUBLIC_ID = "01991f6e-7300-7b21-a3cc-1436db3df95e";
    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

    @Test
    void resolvesBoundaryForSameUserAndRoom() {
        ChatMessageCursorStore store = new ChatMessageCursorStore(
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        LocalDateTime createdAt = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

        String cursor = store.issue(USER_PUBLIC_ID, 3001L, createdAt, 10001L);

        var boundary = store.resolve(cursor, USER_PUBLIC_ID, 3001L);
        assertThat(boundary.beforeCreatedAt()).isEqualTo(createdAt);
        assertThat(boundary.beforeMessageId()).isEqualTo(10001L);
    }

    @Test
    void rejectsCursorFromDifferentContext() {
        ChatMessageCursorStore store = new ChatMessageCursorStore(
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        String cursor = store.issue(
                USER_PUBLIC_ID,
                3001L,
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC),
                10001L
        );

        assertThatThrownBy(() -> store.resolve(cursor, USER_PUBLIC_ID, 3002L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CURSOR)
                );
    }

    @Test
    void rejectsExpiredCursor() {
        MutableClock clock = new MutableClock(NOW);
        ChatMessageCursorStore store = new ChatMessageCursorStore(clock);
        String cursor = store.issue(
                USER_PUBLIC_ID,
                3001L,
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC),
                10001L
        );

        clock.setInstant(NOW.plusSeconds(1_801));

        assertThatThrownBy(() -> store.resolve(cursor, USER_PUBLIC_ID, 3001L))
                .isInstanceOf(BusinessException.class);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
