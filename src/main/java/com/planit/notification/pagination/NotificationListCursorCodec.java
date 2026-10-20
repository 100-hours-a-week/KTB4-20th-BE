package com.planit.notification.pagination;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Component
public class NotificationListCursorCodec {

    public String encode(Cursor cursor) {
        String value = String.join(
                "|",
                cursor.recipientPublicId().toString(),
                cursor.createdAt().toString(),
                cursor.notificationId().toString()
        );
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public Cursor decode(String encodedCursor, UUID recipientPublicId) {
        try {
            String value = new String(
                    Base64.getUrlDecoder().decode(encodedCursor),
                    StandardCharsets.UTF_8
            );
            String[] parts = value.split("\\|", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("잘못된 cursor 형식");
            }

            Cursor cursor = new Cursor(
                    UUID.fromString(parts[0]),
                    LocalDateTime.parse(parts[1]),
                    Long.parseLong(parts[2])
            );
            if (!cursor.recipientPublicId().equals(recipientPublicId)) {
                throw new IllegalArgumentException("다른 사용자의 cursor");
            }
            return cursor;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR, exception);
        }
    }

    public record Cursor(
            UUID recipientPublicId,
            LocalDateTime createdAt,
            Long notificationId
    ) {
    }
}
