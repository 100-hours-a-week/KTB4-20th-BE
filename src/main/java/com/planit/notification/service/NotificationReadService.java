package com.planit.notification.service;

import com.planit.domain.Trip;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationTargetStatus;
import com.planit.notification.dto.NotificationReadResponse;
import com.planit.notification.repository.NotificationRepository;
import com.planit.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationReadService {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    private final NotificationRepository notificationRepository;
    private final TripRepository tripRepository;
    private final Clock clock;

    @Transactional
    public NotificationReadResponse markAsRead(
            String userPublicId,
            Long notificationId
    ) {
        Notification notification = notificationRepository
                .findOwnedActiveById(
                        notificationId,
                        UUID.fromString(userPublicId)
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.NOTIFICATION_NOT_FOUND
                ));
        notification.markAsRead(LocalDateTime.now(clock));

        NotificationTargetStatus targetStatus = resolveTargetStatus(
                notification
        );
        return new NotificationReadResponse(
                notification.getId().toString(),
                true,
                notification.getReadAt().atOffset(SEOUL_OFFSET),
                targetStatus,
                targetStatus == NotificationTargetStatus.ACTIVE
        );
    }

    private NotificationTargetStatus resolveTargetStatus(
            Notification notification
    ) {
        if (notification.getTargetStatus()
                == NotificationTargetStatus.EXPIRED) {
            return NotificationTargetStatus.EXPIRED;
        }

        return tripRepository.findById(notification.getTargetId())
                .filter(trip -> trip.getDeletedAt() == null)
                .map(Trip::getId)
                .map(ignored -> NotificationTargetStatus.ACTIVE)
                .orElse(NotificationTargetStatus.EXPIRED);
    }
}
