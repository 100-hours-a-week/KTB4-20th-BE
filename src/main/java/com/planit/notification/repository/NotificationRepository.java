package com.planit.notification.repository;

import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    Optional<Notification> findByDedupeKey(String dedupeKey);

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
