package com.planit.chat.dto;

import java.time.Instant;

public record RegionalChatRoomLeaveResponse(
        String roomId,
        Instant leftAt
) {
}
