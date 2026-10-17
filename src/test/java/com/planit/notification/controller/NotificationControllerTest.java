package com.planit.notification.controller;

import com.planit.global.error.GlobalExceptionHandler;
import com.planit.notification.domain.NotificationTargetStatus;
import com.planit.notification.domain.NotificationTargetType;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.dto.AllNotificationsReadResponse;
import com.planit.notification.dto.NotificationListResponse;
import com.planit.notification.dto.NotificationReadResponse;
import com.planit.notification.dto.UnreadNotificationCountResponse;
import com.planit.notification.service.NotificationListService;
import com.planit.notification.service.NotificationReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest {

    private static final String USER_PUBLIC_ID =
            "01991f6e-7300-7b21-a3cc-1436db3df95e";

    private NotificationListService service;
    private NotificationReadService readService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(NotificationListService.class);
        readService = mock(NotificationReadService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new NotificationController(service, readService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @DisplayName("현재 사용자의 알림 목록을 조회한다")
    @Test
    void getsNotifications() throws Exception {
        when(service.getNotifications(USER_PUBLIC_ID, "next-cursor"))
                .thenReturn(new NotificationListResponse(
                        List.of(new NotificationListResponse.Item(
                                "16001",
                                NotificationType.TRIP_WEATHER_ALERT,
                                "여행지에 많은 비가 예상돼요",
                                "방장이 일정 변경 여부를 확인하고 있어요.",
                                new NotificationListResponse.Target(
                                        NotificationTargetType.TRIP,
                                        "1001",
                                        NotificationTargetStatus.ACTIVE
                                ),
                                false,
                                null,
                                OffsetDateTime.parse(
                                        "2026-09-07T10:00:00.123456+09:00"
                                )
                        )),
                        new NotificationListResponse.Page(
                                "following-cursor",
                                true
                        )
                ));

        mockMvc.perform(get("/api/notifications")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        ))
                        .queryParam("cursor", "next-cursor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("NOTIFICATIONS_RETRIEVED"))
                .andExpect(jsonPath("$.data.items[0].notificationId")
                        .value("16001"))
                .andExpect(jsonPath("$.data.items[0].target.status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andExpect(jsonPath("$.data.items[0].readAt").isEmpty())
                .andExpect(jsonPath("$.data.page.nextCursor")
                        .value("following-cursor"))
                .andExpect(jsonPath("$.data.page.hasNext").value(true));

        verify(service).getNotifications(USER_PUBLIC_ID, "next-cursor");
    }

    @DisplayName("현재 사용자의 미읽음 알림 수를 조회한다")
    @Test
    void getsUnreadNotificationCount() throws Exception {
        when(service.getUnreadCount(USER_PUBLIC_ID))
                .thenReturn(new UnreadNotificationCountResponse(3L, true));

        mockMvc.perform(get("/api/notifications/unread-count")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("UNREAD_NOTIFICATION_COUNT_RETRIEVED"))
                .andExpect(jsonPath("$.message")
                        .value("읽지 않은 알림 수를 조회했습니다."))
                .andExpect(jsonPath("$.data.unreadCount").value(3))
                .andExpect(jsonPath("$.data.hasUnread").value(true));

        verify(service).getUnreadCount(USER_PUBLIC_ID);
    }

    @DisplayName("현재 사용자의 모든 알림을 읽음 처리한다")
    @Test
    void marksAllNotificationsAsRead() throws Exception {
        when(readService.markAllAsRead(USER_PUBLIC_ID))
                .thenReturn(new AllNotificationsReadResponse(
                        3,
                        OffsetDateTime.parse(
                                "2026-10-17T11:05:00.123456+09:00"
                        ),
                        0L
                ));

        mockMvc.perform(put("/api/notifications/read-all")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("ALL_NOTIFICATIONS_READ"))
                .andExpect(jsonPath("$.message")
                        .value("모든 알림을 읽음 처리했습니다."))
                .andExpect(jsonPath("$.data.updatedCount").value(3))
                .andExpect(jsonPath("$.data.readAt")
                        .value("2026-10-17T11:05:00.123456+09:00"))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        verify(readService).markAllAsRead(USER_PUBLIC_ID);
    }

    @DisplayName("현재 사용자의 알림을 읽음 처리한다")
    @Test
    void marksNotificationAsRead() throws Exception {
        when(readService.markAsRead(USER_PUBLIC_ID, 16001L))
                .thenReturn(new NotificationReadResponse(
                        "16001",
                        true,
                        OffsetDateTime.parse(
                                "2026-10-17T11:00:00.123456+09:00"
                        ),
                        NotificationTargetStatus.EXPIRED,
                        false
                ));

        mockMvc.perform(put("/api/notifications/16001/read")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_READ"))
                .andExpect(jsonPath("$.data.notificationId")
                        .value("16001"))
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.readAt")
                        .value("2026-10-17T11:00:00.123456+09:00"))
                .andExpect(jsonPath("$.data.targetStatus")
                        .value("EXPIRED"))
                .andExpect(jsonPath("$.data.navigable").value(false));

        verify(readService).markAsRead(USER_PUBLIC_ID, 16001L);
    }
}
