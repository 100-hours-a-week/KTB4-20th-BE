package com.planit.notification.delivery;

import com.planit.notification.domain.NotificationType;
import com.planit.notification.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationPublishTargetService {

    private static final List<NotificationType> PUBLISHABLE_TYPES = List.of(
            NotificationType.TRIP_D1,
            NotificationType.TRIP_WEATHER_ALERT
    );

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationPublishTargetService(
            NotificationRepository notificationRepository,
            @Qualifier("tripRemindClock") Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NotificationPublishEvent> findTodayUndelivered() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime startInclusive = today.atStartOfDay();
        LocalDateTime endExclusive = today.plusDays(1).atStartOfDay();

        return notificationRepository.findUndeliveredCreatedBetween(
                        startInclusive,
                        endExclusive,
                        PUBLISHABLE_TYPES
                ).stream()
                .map(NotificationPublishEvent::from)
                .toList();
    }
}
