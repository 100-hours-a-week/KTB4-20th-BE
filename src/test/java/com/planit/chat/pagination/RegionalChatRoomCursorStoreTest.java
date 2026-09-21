package com.planit.chat.pagination;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegionalChatRoomCursorStoreTest {

    private static final String USER_PUBLIC_ID = "01991f6e-7300-7b21-a3cc-1436db3df95e";

    @Test
    void keepsSnapshotOrderAcrossPagesWithoutExtendingAbsoluteExpiry() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-20T00:00:00Z"));
        RegionalChatRoomCursorStore store = new RegionalChatRoomCursorStore(clock);
        List<Long> orderedRoomIds = LongStream.rangeClosed(1, 40).boxed().toList();
        String cursor = store.createSnapshot(USER_PUBLIC_ID, orderedRoomIds, 20);

        RegionalChatRoomCursorStore.CursorPage secondPage = store.resolve(
                cursor,
                USER_PUBLIC_ID,
                10
        );

        assertThat(secondPage.roomIds()).containsExactlyElementsOf(
                LongStream.rangeClosed(21, 30).boxed().toList()
        );
        assertThat(secondPage.hasNext()).isTrue();

        clock.advance(Duration.ofMinutes(4));
        RegionalChatRoomCursorStore.CursorPage thirdPage = store.resolve(
                secondPage.nextCursor(),
                USER_PUBLIC_ID,
                10
        );
        assertThat(thirdPage.roomIds()).containsExactlyElementsOf(
                LongStream.rangeClosed(31, 40).boxed().toList()
        );
        assertThat(thirdPage.hasNext()).isFalse();

        clock.advance(Duration.ofMinutes(1));
        assertThatThrownBy(() -> store.resolve(cursor, USER_PUBLIC_ID, 10))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CURSOR)
                );
    }

    @Test
    void rejectsCursorFromAnotherUser() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-20T00:00:00Z"));
        RegionalChatRoomCursorStore store = new RegionalChatRoomCursorStore(clock);
        String cursor = store.createSnapshot(USER_PUBLIC_ID, List.of(1L, 2L), 1);

        assertThatThrownBy(() -> store.resolve(cursor, UUID_OTHER_USER, 10))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_CURSOR)
                );
    }

    @Test
    void invalidatesEverySnapshotForUser() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-20T00:00:00Z"));
        RegionalChatRoomCursorStore store = new RegionalChatRoomCursorStore(clock);
        String firstCursor = store.createSnapshot(USER_PUBLIC_ID, List.of(1L, 2L), 1);
        String secondCursor = store.createSnapshot(USER_PUBLIC_ID, List.of(3L, 4L), 1);

        store.invalidateUserSnapshots(USER_PUBLIC_ID);

        assertThatThrownBy(() -> store.resolve(firstCursor, USER_PUBLIC_ID, 10))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> store.resolve(secondCursor, USER_PUBLIC_ID, 10))
                .isInstanceOf(BusinessException.class);
    }

    private static final String UUID_OTHER_USER = "01991f6e-7300-7b21-a3cc-1436db3df95f";

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
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
