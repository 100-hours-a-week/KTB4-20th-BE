package com.planit.chat.pagination;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatMessageCursorStore {

    private static final Duration CURSOR_TTL = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, CursorState> cursors = new ConcurrentHashMap<>();
    private final Clock clock;

    public ChatMessageCursorStore() {
        this(Clock.systemUTC());
    }

    ChatMessageCursorStore(Clock clock) {
        this.clock = clock;
    }

    public String issue(
            String userPublicId,
            Long roomId,
            LocalDateTime beforeCreatedAt,
            Long beforeMessageId
    ) {
        removeExpired();
        String token = UUID.randomUUID().toString();
        cursors.put(token, new CursorState(
                userPublicId,
                roomId,
                beforeCreatedAt,
                beforeMessageId,        //CreatedAt과 MessageId를 함께 커서에 사용하여 tie break.
                Instant.now(clock).plus(CURSOR_TTL)
        ));
        return token;
    }

    public CursorBoundary resolve(String token, String userPublicId, Long roomId) {
        removeExpired();
        CursorState state = cursors.get(token);
        if (state == null
                || !state.userPublicId().equals(userPublicId)
                || !state.roomId().equals(roomId)) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
        return new CursorBoundary(state.beforeCreatedAt(), state.beforeMessageId());
    }

    private void removeExpired() {
        Instant now = Instant.now(clock);
        cursors.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    public record CursorBoundary(
            LocalDateTime beforeCreatedAt,
            Long beforeMessageId
    ) {
    }

    private record CursorState(
            String userPublicId,
            Long roomId,
            LocalDateTime beforeCreatedAt,
            Long beforeMessageId,
            Instant expiresAt
    ) {
    }
}
