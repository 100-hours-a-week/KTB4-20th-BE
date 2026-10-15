package com.planit.notification.repository;

import com.planit.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    Optional<Notification> findByDedupeKey(String dedupeKey);
}
