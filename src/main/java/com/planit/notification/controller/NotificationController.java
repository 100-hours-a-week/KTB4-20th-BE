package com.planit.notification.controller;

import com.planit.global.response.ApiResponse;
import com.planit.notification.dto.NotificationListResponse;
import com.planit.notification.dto.UnreadNotificationCountResponse;
import com.planit.notification.service.NotificationListService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationListService notificationListService;

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
}
