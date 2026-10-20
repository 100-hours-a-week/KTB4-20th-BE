package com.planit.notification.controller;

import com.planit.global.response.ApiResponse;
import com.planit.notification.dto.AllNotificationsReadResponse;
import com.planit.notification.dto.NotificationListResponse;
import com.planit.notification.dto.NotificationReadResponse;
import com.planit.notification.dto.PendingNotificationDeliveriesResponse;
import com.planit.notification.dto.UnreadNotificationCountResponse;
import com.planit.notification.service.NotificationListService;
import com.planit.notification.service.NotificationDeliveryService;
import com.planit.notification.service.NotificationReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationListService notificationListService;
    private final NotificationReadService notificationReadService;
    private final NotificationDeliveryService notificationDeliveryService;

    @GetMapping
    public ApiResponse<NotificationListResponse> getNotifications(
            Authentication authentication,
            @RequestParam(required = false) String cursor
    ) {
        return ApiResponse.success(
                "NOTIFICATIONS_RETRIEVED",
                "알림 목록을 조회했습니다.",
                notificationListService.getNotifications(
                        authentication.getName(),
                        cursor
                )
        );
    }

    @GetMapping("/unread-count")
    public ApiResponse<UnreadNotificationCountResponse> getUnreadCount(
            Authentication authentication
    ) {
        return ApiResponse.success(
                "UNREAD_NOTIFICATION_COUNT_RETRIEVED",
                "읽지 않은 알림 수를 조회했습니다.",
                notificationListService.getUnreadCount(
                        authentication.getName()
                )
        );
    }

    @GetMapping("/pending-deliveries")
    public ApiResponse<PendingNotificationDeliveriesResponse>
            getPendingDeliveries(Authentication authentication) {
        return ApiResponse.success(
                "PENDING_NOTIFICATION_DELIVERIES_RETRIEVED",
                "미전달 알림을 조회했습니다.",
                notificationListService.getPendingDeliveries(
                        authentication.getName()
                )
        );
    }

    @PutMapping("/read-all")
    public ApiResponse<AllNotificationsReadResponse> markAllAsRead(
            Authentication authentication
    ) {
        return ApiResponse.success(
                "ALL_NOTIFICATIONS_READ",
                "모든 알림을 읽음 처리했습니다.",
                notificationReadService.markAllAsRead(
                        authentication.getName()
                )
        );
    }

    @PutMapping("/{notificationId}/read")
    public ApiResponse<NotificationReadResponse> markAsRead(
            Authentication authentication,
            @PathVariable Long notificationId
    ) {
        return ApiResponse.success(
                "NOTIFICATION_READ",
                "알림을 읽음 처리했습니다.",
                notificationReadService.markAsRead(
                        authentication.getName(),
                        notificationId
                )
        );
    }

    @PutMapping("/{notificationId}/delivery-ack")
    public ApiResponse<Void> acknowledgeDelivery(
                    Authentication authentication,
                    @PathVariable Long notificationId
            ) {
        notificationDeliveryService.acknowledge(
                authentication.getName(),
                notificationId
        );
        return ApiResponse.success(
                "NOTIFICATION_DELIVERY_ACKNOWLEDGED",
                "알림 수신을 확인했습니다.",
                null
        );
    }
}
