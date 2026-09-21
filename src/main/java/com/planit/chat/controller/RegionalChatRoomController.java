package com.planit.chat.controller;

import com.planit.chat.dto.RegionalChatRoomListResponse;
import com.planit.chat.service.RegionalChatRoomListService;
import com.planit.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/regional-chat-rooms")
@RequiredArgsConstructor
public class RegionalChatRoomController {

    private static final String SUCCESS_CODE = "REGIONAL_CHAT_ROOMS_RETRIEVED";
    private static final String SUCCESS_MESSAGE = "지역 채팅방 목록을 조회했습니다.";

    private final RegionalChatRoomListService regionalChatRoomListService;

    @GetMapping
    public ApiResponse<RegionalChatRoomListResponse> getRegionalChatRooms(
            Authentication authentication,
            @RequestParam(required = false) String cursor
    ) {
        RegionalChatRoomListResponse response = regionalChatRoomListService
                .getRegionalChatRooms(authentication.getName(), cursor);
        return ApiResponse.success(SUCCESS_CODE, SUCCESS_MESSAGE, response);
    }
}
