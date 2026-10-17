package com.planit.notification.repository;

import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    Optional<Notification> findByDedupeKey(String dedupeKey);

    long countByRecipientPublicIdAndReadAtIsNullAndDeletedAtIsNull(
            UUID recipientPublicId
    );

    @Query("""
            SELECT notification
            FROM Notification notification
            JOIN FETCH notification.recipient recipient
            WHERE recipient.publicId = :recipientPublicId
              AND notification.deletedAt IS NULL
              AND (
                :cursorCreatedAt IS NULL
                OR notification.createdAt < :cursorCreatedAt
                OR (
                  notification.createdAt = :cursorCreatedAt
                  AND notification.id < :cursorId
                )
              )
            ORDER BY notification.createdAt DESC, notification.id DESC
            """)
    List<Notification> findPageForRecipient(
            @Param("recipientPublicId") UUID recipientPublicId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
            SELECT notification
            FROM Notification notification
            JOIN FETCH notification.recipient
            WHERE notification.createdAt >= :startInclusive
              AND notification.createdAt < :endExclusive
              AND notification.deliveredAt IS NULL
              AND notification.deletedAt IS NULL
              AND notification.type IN :types
            ORDER BY notification.id
            """)
    List<Notification> findUndeliveredCreatedBetween(
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            @Param("types") List<NotificationType> types
    );
}
