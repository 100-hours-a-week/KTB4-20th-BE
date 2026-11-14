package com.planit.notification.service;

import com.planit.domain.Trip;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationTargetStatus;
import com.planit.notification.domain.NotificationTargetType;
import com.planit.notification.dto.AllNotificationsReadResponse;
import com.planit.notification.dto.NotificationReadResponse;
import com.planit.notification.repository.NotificationRepository;
import com.planit.photomission.repository.MissionRepository;
import com.planit.repository.TripRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class NotificationReadService {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    private final NotificationRepository notificationRepository;
    private final TripRepository tripRepository;
    private final MissionRepository missionRepository;
    private final Clock clock;

    public NotificationReadService(
            NotificationRepository notificationRepository,
            TripRepository tripRepository,
            MissionRepository missionRepository,
            @Qualifier("tripRemindClock") Clock clock
    ) {
        this.notificationRepository = notificationRepository;
        this.tripRepository = tripRepository;
        this.missionRepository = missionRepository;
        this.clock = clock;
    }

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

    @Transactional
    public AllNotificationsReadResponse markAllAsRead(
            String userPublicId
    ) {
        UUID recipientPublicId = UUID.fromString(userPublicId);
        LocalDateTime readAt = LocalDateTime.now(clock);
        int updatedCount = notificationRepository.markAllAsRead(
                recipientPublicId,
                readAt
        );
        long unreadCount = notificationRepository
                .countByRecipientPublicIdAndReadAtIsNullAndDeletedAtIsNull(
                        recipientPublicId
                );

        return new AllNotificationsReadResponse(
                updatedCount,
                readAt.atOffset(SEOUL_OFFSET),
                unreadCount
        );
    }

    private NotificationTargetStatus resolveTargetStatus(
            Notification notification
    ) {
        if (notification.getTargetStatus()
                == NotificationTargetStatus.EXPIRED) {
            return NotificationTargetStatus.EXPIRED;
        }

        NotificationTargetType targetType = notification.getTargetType();
        if (targetType == NotificationTargetType.TRIP) {
            return tripRepository.findById(notification.getTargetId())
                    .filter(trip -> trip.getDeletedAt() == null)
                    .map(Trip::getId)
                    .map(ignored -> NotificationTargetStatus.ACTIVE)
                    .orElse(NotificationTargetStatus.EXPIRED);
        }
        if (targetType == NotificationTargetType.PHOTO_MISSION) {
            return missionRepository.findById(notification.getTargetId())
                    .filter(mission -> mission.getTrip().getDeletedAt() == null)
                    .map(ignored -> NotificationTargetStatus.ACTIVE)
                    .orElse(NotificationTargetStatus.EXPIRED);
        }
        return NotificationTargetStatus.EXPIRED;
    }
}
