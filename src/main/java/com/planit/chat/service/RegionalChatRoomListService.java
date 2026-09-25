package com.planit.chat.service;

import com.planit.chat.dto.RegionalChatRoomListResponse;

public interface RegionalChatRoomListService {

    RegionalChatRoomListResponse getRegionalChatRooms(String userPublicId);
}
