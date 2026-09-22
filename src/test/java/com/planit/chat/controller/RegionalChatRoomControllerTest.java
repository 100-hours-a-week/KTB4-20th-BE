package com.planit.chat.controller;

import com.planit.chat.dto.RegionalChatRoomListResponse;
import com.planit.chat.dto.RegionalChatRoomListResponse.CursorPageResponse;
import com.planit.chat.dto.RegionalChatRoomListResponse.RegionalChatRoomItemResponse;
import com.planit.chat.service.RegionalChatRoomListService;
import com.planit.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegionalChatRoomControllerTest {

    private static final String USER_PUBLIC_ID = "01991f6e-7300-7b21-a3cc-1436db3df95e";

    private RegionalChatRoomListService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(RegionalChatRoomListService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RegionalChatRoomController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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
}
