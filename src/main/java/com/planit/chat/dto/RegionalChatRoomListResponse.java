package com.planit.chat.dto;

import java.util.List;

public record RegionalChatRoomListResponse(
        List<RegionalChatRoomItemResponse> items
) {

    public RegionalChatRoomListResponse {
        items = List.copyOf(items);
    }

    public record RegionalChatRoomItemResponse(
            String roomId,
            String regionId,
            String name,
            long memberCount,
            long activeUserCount,
            boolean relatedToMyTrip,
            boolean joined,
            boolean canJoin
    ) {
    }
}
