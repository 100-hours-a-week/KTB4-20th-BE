package com.planit.chat.pagination;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RegionalChatRoomCursorStore {

    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, RoomOrderSnapshot> snapshots = new ConcurrentHashMap<>(); //스냅샷 저장소
    private final ConcurrentHashMap<String, CursorPointer> cursors = new ConcurrentHashMap<>();       //커서 저장소
    private final Clock clock;

    public RegionalChatRoomCursorStore() {
        this(Clock.systemUTC());
    }

    RegionalChatRoomCursorStore(Clock clock) { //시간 고정 후 테스트를 위한 생성자 메소드 오버로딩
        this.clock = clock;
    }

    public synchronized String createSnapshot(  //새로운 스냅샷 생성
            String userPublicId,
            List<Long> orderedRoomIds,
            int nextIndex
    ) {
        removeExpired();
        String snapshotId = UUID.randomUUID().toString();
        snapshots.put(snapshotId, new RoomOrderSnapshot(
                userPublicId,
                List.copyOf(orderedRoomIds),
                Instant.now(clock).plus(SNAPSHOT_TTL)
        ));
        return issueCursor(snapshotId, nextIndex);
    }

    public synchronized CursorPage resolve(String token, String userPublicId, int pageSize) {   //Cursor 사용
        removeExpired();
        CursorPointer pointer = cursors.get(token);
        if (pointer == null) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }

        RoomOrderSnapshot snapshot = snapshots.get(pointer.snapshotId());
        if (snapshot == null || !snapshot.userPublicId().equals(userPublicId)) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }

        int fromIndex = Math.min(pointer.nextIndex(), snapshot.orderedRoomIds().size());
        int toIndex = Math.min(fromIndex + pageSize, snapshot.orderedRoomIds().size());
        boolean hasNext = toIndex < snapshot.orderedRoomIds().size();
        String nextCursor = hasNext ? issueCursor(pointer.snapshotId(), toIndex) : null;

        return new CursorPage(
                snapshot.orderedRoomIds().subList(fromIndex, toIndex),
                nextCursor,
                hasNext
        );
    }

    public synchronized void invalidateUserSnapshots(String userPublicId) {
        snapshots.entrySet().removeIf(entry ->
                entry.getValue().userPublicId().equals(userPublicId)
        );
        removeOrphanedCursors();
    }

    private String issueCursor(String snapshotId, int nextIndex) {
        String token = UUID.randomUUID().toString();
        cursors.put(token, new CursorPointer(snapshotId, nextIndex));
        return token;
    }

    private void removeExpired() {
        Instant now = Instant.now(clock);
        snapshots.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        removeOrphanedCursors();
    }

    private void removeOrphanedCursors() {
        cursors.entrySet().removeIf(entry -> !snapshots.containsKey(entry.getValue().snapshotId()));
    }

    public record CursorPage(
            List<Long> roomIds,
            String nextCursor,
            boolean hasNext
    ) {

        public CursorPage {
            roomIds = List.copyOf(roomIds);
        }
    }

    private record RoomOrderSnapshot(
            String userPublicId,
            List<Long> orderedRoomIds,
            Instant expiresAt
    ) {
    }

    private record CursorPointer(String snapshotId, int nextIndex) {
    }
}
