package com.planit.notification.service;

import com.planit.domain.Trip;
import com.planit.notification.domain.Notification;
import com.planit.notification.domain.NotificationTargetStatus;
import com.planit.notification.dto.NotificationListResponse;
import com.planit.notification.dto.UnreadNotificationCountResponse;
import com.planit.notification.pagination.NotificationListCursorCodec;
import com.planit.notification.pagination.NotificationListCursorCodec.Cursor;
import com.planit.notification.repository.NotificationRepository;
import com.planit.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationListService {

    private static final int FIRST_PAGE_SIZE = 20;
    private static final int NEXT_PAGE_SIZE = 10;
    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    private final NotificationRepository notificationRepository;
    private final TripRepository tripRepository;
    private final NotificationListCursorCodec cursorCodec;

    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(
            String userPublicId,
            String encodedCursor
    ) {
        UUID recipientPublicId = UUID.fromString(userPublicId);
        Cursor cursor = encodedCursor == null
                ? null
                : cursorCodec.decode(encodedCursor, recipientPublicId);
        int pageSize = cursor == null ? FIRST_PAGE_SIZE : NEXT_PAGE_SIZE;

        List<Notification> found = notificationRepository.findPageForRecipient(
                recipientPublicId,
                cursor == null ? null : cursor.createdAt(),
                cursor == null ? null : cursor.notificationId(),
                PageRequest.of(0, pageSize + 1)
        );
        boolean hasNext = found.size() > pageSize;
        List<Notification> page = found.subList(0, Math.min(pageSize, found.size()));
        Set<Long> activeTripIds = findActiveTripIds(page);

        List<NotificationListResponse.Item> items = page.stream()
                .map(notification -> toItem(notification, activeTripIds))
                .toList();
        String nextCursor = hasNext
                ? encodeCursor(recipientPublicId, page.getLast())
                : null;

        return new NotificationListResponse(
                items,
                new NotificationListResponse.Page(nextCursor, hasNext)
        );
    }

    @Transactional(readOnly = true)
    public UnreadNotificationCountResponse getUnreadCount(
            String userPublicId
    ) {
        long unreadCount = notificationRepository
                .countByRecipientPublicIdAndReadAtIsNullAndDeletedAtIsNull(
                        UUID.fromString(userPublicId)
                );
        return new UnreadNotificationCountResponse(
                unreadCount,
                unreadCount > 0
        );
    }

    private Set<Long> findActiveTripIds(List<Notification> notifications) {
        List<Long> targetIds = notifications.stream()
                .filter(notification -> notification.getTargetType() != null)
                .map(Notification::getTargetId)
                .distinct()
                .toList();
        if (targetIds.isEmpty()) {
            return Set.of();
        }

        Set<Long> activeTripIds = new HashSet<>();
        tripRepository.findAllById(targetIds).stream()
                .filter(trip -> trip.getDeletedAt() == null)
                .map(Trip::getId)
                .forEach(activeTripIds::add);
        return activeTripIds;
    }

    private NotificationListResponse.Item toItem(
            Notification notification,
            Set<Long> activeTripIds
    ) {
        return new NotificationListResponse.Item(
                notification.getId().toString(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getTargetType() == null
                        ? null
                        : new NotificationListResponse.Target(
                                notification.getTargetType(),
                                notification.getTargetId().toString(),
                                notification.getTargetStatus()
                                        == NotificationTargetStatus.ACTIVE
                                        && activeTripIds.contains(
                                                notification.getTargetId()
                                        )
                                        ? NotificationTargetStatus.ACTIVE
                                        : NotificationTargetStatus.EXPIRED
                        ),
                notification.getReadAt() != null,
                toOffset(notification.getReadAt()),
                toOffset(notification.getCreatedAt())
        );
    }

    private String encodeCursor(UUID recipientPublicId, Notification last) {
        return cursorCodec.encode(new Cursor(
                recipientPublicId,
                last.getCreatedAt(),
                last.getId()
        ));
    }

    private java.time.OffsetDateTime toOffset(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atOffset(SEOUL_OFFSET);
    }
}
