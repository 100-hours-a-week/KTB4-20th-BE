package com.planit.notification.pagination;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.notification.pagination.NotificationListCursorCodec.Cursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationListCursorCodecTest {

    private final NotificationListCursorCodec codec =
            new NotificationListCursorCodec();

    @DisplayName("알림 목록 커서에 사용자와 마지막 정렬 키를 보존한다")
    @Test
    void restoresEncodedCursor() {
        UUID recipientPublicId = UUID.randomUUID();
        Cursor cursor = new Cursor(
                recipientPublicId,
                LocalDateTime.of(2026, 10, 16, 10, 0),
                16001L
        );

        assertThat(codec.decode(codec.encode(cursor), recipientPublicId))
                .isEqualTo(cursor);
    }

    @DisplayName("다른 사용자의 알림 목록 커서를 거부한다")
    @Test
    void rejectsAnotherUsersCursor() {
        Cursor cursor = new Cursor(
                UUID.randomUUID(),
                LocalDateTime.of(2026, 10, 16, 10, 0),
                16001L
        );

        assertThatThrownBy(() -> codec.decode(
                codec.encode(cursor),
                UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }

    @DisplayName("형식이 잘못된 알림 목록 커서를 거부한다")
    @Test
    void rejectsMalformedCursor() {
        assertThatThrownBy(() -> codec.decode(
                "invalid-cursor",
                UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CURSOR);
    }
}
