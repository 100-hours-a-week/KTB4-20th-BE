package com.planit.chat.websocket.message;

public final class ChatMessageDestinations {

    private static final String ROOM_DESTINATION_PREFIX =
            "/topic/regional-chat-rooms/";
    private static final String ROOM_DESTINATION_SUFFIX = "/messages";

    private ChatMessageDestinations() {
    }

    public static String roomMessages(long roomId) {
        return ROOM_DESTINATION_PREFIX + roomId + ROOM_DESTINATION_SUFFIX;
    }
}
