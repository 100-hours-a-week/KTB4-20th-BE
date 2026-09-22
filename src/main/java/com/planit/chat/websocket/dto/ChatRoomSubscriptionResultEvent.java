package com.planit.chat.websocket.dto;

import java.time.Instant;

public record ChatRoomSubscriptionResultEvent(
        String eventType,
        String status,
        String code,
        String message,
        String roomId,
        Instant occurredAt,
        Object data
) {

    private static final String EVENT_TYPE = "CHAT_ROOM_SUBSCRIPTION_RESULT";

    public static ChatRoomSubscriptionResultEvent accepted(Long roomId) {
        return new ChatRoomSubscriptionResultEvent(
                EVENT_TYPE,
                "ACCEPTED",
                "CHAT_ROOM_SUBSCRIBED",
                "채팅방 실시간 구독을 시작했습니다.",
                roomId.toString(),
                Instant.now(),
                null
        );
    }

    public static ChatRoomSubscriptionResultEvent rejected(
            Long roomId,
            String code,
            String message
    ) {
        return new ChatRoomSubscriptionResultEvent(
                EVENT_TYPE,
                "REJECTED",
                code,
                message,
                roomId == null ? null : roomId.toString(),
                Instant.now(),
                null
        );
    }
}
