package com.planit.notification.repository;

import com.planit.notification.domain.Notification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class NotificationStore {

    private static final String UPSERT_SQL = """
            INSERT INTO notifications (
                user_id,
                title,
                body,
                notification_type,
                target_type,
                target_id,
                target_status,
                dedupe_key,
                created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
            """;

    private static final String ACK_SQL = """
            UPDATE notifications
            SET delivered_at = ?
            WHERE id = ?
              AND user_id = ?
              AND deleted_at IS NULL
              AND delivered_at IS NULL
            """;

    private static final String FIND_DELIVERED_AT_SQL = """
            SELECT delivered_at
            FROM notifications
            WHERE id = ?
              AND user_id = ?
              AND deleted_at IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;

    public NotificationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Long saveIdempotently(Notification notification) {
        Objects.requireNonNull(notification, "저장할 알림은 필수입니다");
        Long recipientId = Objects.requireNonNull(
                notification.getRecipient().getId(),
                "저장된 알림 수신자 ID는 필수입니다"
        );

        jdbcTemplate.update(
                UPSERT_SQL,
                recipientId,
                notification.getTitle(),
                notification.getBody(),
                notification.getType().name(),
                notification.getTargetType().name(),
                notification.getTargetId(),
                notification.getTargetStatus().name(),
                notification.getDedupeKey(),
                notification.getCreatedAt()
        );

        return Objects.requireNonNull(
                jdbcTemplate.queryForObject(
                        "SELECT LAST_INSERT_ID()",
                        Long.class
                ),
                "저장된 알림 ID를 조회할 수 없습니다"
        );
    }

    @Transactional
    public Optional<LocalDateTime> acknowledgeDelivery(
            Long notificationId,
            Long recipientId,
            LocalDateTime acknowledgedAt
    ) {
        Objects.requireNonNull(notificationId, "알림 ID는 필수입니다");
        Objects.requireNonNull(recipientId, "알림 수신자 ID는 필수입니다");
        Objects.requireNonNull(acknowledgedAt, "알림 수신 확인 시각은 필수입니다");

        jdbcTemplate.update(
                ACK_SQL,
                acknowledgedAt,
                notificationId,
                recipientId
        );

        List<LocalDateTime> deliveredAt = jdbcTemplate.query(
                FIND_DELIVERED_AT_SQL,
                (resultSet, rowNumber) -> resultSet.getObject(
                        "delivered_at",
                        LocalDateTime.class
                ),
                notificationId,
                recipientId
        );
        return deliveredAt.stream().findFirst();
    }
}
