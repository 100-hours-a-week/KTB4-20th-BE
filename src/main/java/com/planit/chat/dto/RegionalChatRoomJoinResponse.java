package com.planit.chat.dto;

import java.time.Instant;

public record RegionalChatRoomJoinResponse(
        String roomId,
        Instant joinedAt
) {
}
