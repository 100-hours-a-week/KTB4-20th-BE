package com.planit.chat.controller;

import com.planit.chat.dto.RegionalChatRoomListResponse;
import com.planit.chat.dto.RegionalChatRoomJoinResponse;
import com.planit.chat.dto.RegionalChatRoomLeaveResponse;
import com.planit.chat.service.RegionalChatRoomListService;
import com.planit.chat.service.RegionalChatRoomMembershipService;
import com.planit.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
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
    private final RegionalChatRoomMembershipService membershipService;

    @GetMapping
    public ApiResponse<RegionalChatRoomListResponse> getRegionalChatRooms(
            Authentication authentication,
            @RequestParam(required = false) String cursor
    ) {
        RegionalChatRoomListResponse response = regionalChatRoomListService
                .getRegionalChatRooms(authentication.getName(), cursor);
        return ApiResponse.success(SUCCESS_CODE, SUCCESS_MESSAGE, response);
    }

    @PutMapping("/{roomId}/members/me")
    public ApiResponse<RegionalChatRoomJoinResponse> join(
            Authentication authentication,
            @PathVariable Long roomId
    ) {
        return ApiResponse.success(
                "REGIONAL_CHAT_ROOM_JOINED",
                "지역 채팅방에 입장했습니다.",
                membershipService.join(authentication.getName(), roomId)
        );
    }

    @DeleteMapping("/{roomId}/members/me")
    public ApiResponse<RegionalChatRoomLeaveResponse> leave(
            Authentication authentication,
            @PathVariable Long roomId
    ) {
        return ApiResponse.success(
                "REGIONAL_CHAT_ROOM_LEFT",
                "지역 채팅방에서 나갔습니다.",
                membershipService.leave(authentication.getName(), roomId)
        );
    }
}
