package com.planit.chat.controller;

import com.planit.chat.dto.RegionalChatRoomListResponse;
import com.planit.chat.dto.ChatMessageHistoryResponse;
import com.planit.chat.dto.ChatMessageHistoryResponse.ChatMessageItemResponse;
import com.planit.chat.dto.ChatMessageHistoryResponse.ChatMessagePageResponse;
import com.planit.chat.dto.ChatMessageHistoryResponse.ChatSenderResponse;
import com.planit.chat.service.ChatMessageHistoryService;
import com.planit.chat.dto.RegionalChatRoomListResponse.CursorPageResponse;
import com.planit.chat.dto.RegionalChatRoomListResponse.RegionalChatRoomItemResponse;
import com.planit.chat.dto.RegionalChatRoomJoinResponse;
import com.planit.chat.dto.RegionalChatRoomLeaveResponse;
import com.planit.chat.service.RegionalChatRoomListService;
import com.planit.chat.service.RegionalChatRoomMembershipService;
import com.planit.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegionalChatRoomControllerTest {

    private static final String USER_PUBLIC_ID = "01991f6e-7300-7b21-a3cc-1436db3df95e";

    private RegionalChatRoomListService service;
    private RegionalChatRoomMembershipService membershipService;
    private ChatMessageHistoryService messageHistoryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(RegionalChatRoomListService.class);
        membershipService = mock(RegionalChatRoomMembershipService.class);
        messageHistoryService = mock(ChatMessageHistoryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new RegionalChatRoomController(
                                service,
                                membershipService,
                                messageHistoryService
                        )
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getsLatestChatMessages() throws Exception {
        ChatMessageHistoryResponse response = new ChatMessageHistoryResponse(
                List.of(new ChatMessageItemResponse(
                        "10001",
                        "019b1234-5678-7000-8000-123456789abc",
                        "TEXT",
                        "경주역 근처 맛집 추천해주세요.",
                        null,
                        new ChatSenderResponse(
                                USER_PUBLIC_ID,
                                "플랜잇사용자",
                                "http://localhost:8080/images/default-profile.svg"
                        ),
                        Instant.parse("2026-09-07T11:05:00.123456Z")
                )),
                new ChatMessagePageResponse("next-cursor", null, true)
        );
        when(messageHistoryService.getMessages(USER_PUBLIC_ID, 3001L, null, null))
                .thenReturn(response);

        mockMvc.perform(get("/api/regional-chat-rooms/3001/messages")
                        .principal(new TestingAuthenticationToken(USER_PUBLIC_ID, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CHAT_MESSAGES_RETRIEVED"))
                .andExpect(jsonPath("$.data.items[0].messageId").value("10001"))
                .andExpect(jsonPath("$.data.items[0].text")
                        .value("경주역 근처 맛집 추천해주세요."))
                .andExpect(jsonPath("$.data.page.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.data.page.nextAfterMessageId").isEmpty())
                .andExpect(jsonPath("$.data.page.hasNext").value(true));

        verify(messageHistoryService).getMessages(USER_PUBLIC_ID, 3001L, null, null);
    }

    @Test
    void getsRegionalChatRooms() throws Exception {
        RegionalChatRoomListResponse response = new RegionalChatRoomListResponse(
                List.of(new RegionalChatRoomItemResponse(
                        "3001", "123", "부산광역시 해운대구",
                        128, 24, true, false, true
                )),
                new CursorPageResponse("next-cursor", true)
        );
        when(service.getRegionalChatRooms(USER_PUBLIC_ID, "cursor"))
                .thenReturn(response);

        mockMvc.perform(get("/api/regional-chat-rooms")
                        .param("cursor", "cursor")
                        .principal(new TestingAuthenticationToken(USER_PUBLIC_ID, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REGIONAL_CHAT_ROOMS_RETRIEVED"))
                .andExpect(jsonPath("$.message").value("지역 채팅방 목록을 조회했습니다."))
                .andExpect(jsonPath("$.data.items[0].roomId").value("3001"))
                .andExpect(jsonPath("$.data.items[0].activeUserCount").value(24))
                .andExpect(jsonPath("$.data.items[0].relatedToMyTrip").value(true))
                .andExpect(jsonPath("$.data.page.nextCursor").value("next-cursor"))
                .andExpect(jsonPath("$.data.page.hasNext").value(true));

        verify(service).getRegionalChatRooms(USER_PUBLIC_ID, "cursor");
    }

    @Test
    void joinsRegionalChatRoom() throws Exception {
        Instant joinedAt = Instant.parse("2026-09-20T01:00:00.123456Z");
        when(membershipService.join(USER_PUBLIC_ID, 3001L))
                .thenReturn(new RegionalChatRoomJoinResponse("3001", joinedAt));

        mockMvc.perform(put("/api/regional-chat-rooms/3001/members/me")
                        .principal(new TestingAuthenticationToken(USER_PUBLIC_ID, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REGIONAL_CHAT_ROOM_JOINED"))
                .andExpect(jsonPath("$.data.roomId").value("3001"))
                .andExpect(jsonPath("$.data.joinedAt")
                        .value("2026-09-20T01:00:00.123456Z"));

        verify(membershipService).join(USER_PUBLIC_ID, 3001L);
    }

    @Test
    void leavesRegionalChatRoom() throws Exception {
        Instant leftAt = Instant.parse("2026-09-20T02:00:00.123456Z");
        when(membershipService.leave(USER_PUBLIC_ID, 3001L))
                .thenReturn(new RegionalChatRoomLeaveResponse("3001", leftAt));

        mockMvc.perform(delete("/api/regional-chat-rooms/3001/members/me")
                        .principal(new TestingAuthenticationToken(USER_PUBLIC_ID, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REGIONAL_CHAT_ROOM_LEFT"))
                .andExpect(jsonPath("$.data.roomId").value("3001"))
                .andExpect(jsonPath("$.data.leftAt")
                        .value("2026-09-20T02:00:00.123456Z"));

        verify(membershipService).leave(USER_PUBLIC_ID, 3001L);
    }
}
