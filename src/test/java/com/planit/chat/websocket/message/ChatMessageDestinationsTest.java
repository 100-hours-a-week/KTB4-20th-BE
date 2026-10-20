package com.planit.chat.websocket.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageDestinationsTest {

    @DisplayName("채팅방 ID를 기존 STOMP 메시지 토픽으로 변환한다")
    @Test
    void buildsRoomMessageDestination() {
        assertThat(ChatMessageDestinations.roomMessages(12L))
                .isEqualTo("/topic/regional-chat-rooms/12/messages");
    }

    @DisplayName("서로 다른 채팅방은 서로 다른 메시지 토픽을 사용한다")
    @Test
    void separatesDestinationsByRoomId() {
        assertThat(ChatMessageDestinations.roomMessages(12L))
                .isNotEqualTo(ChatMessageDestinations.roomMessages(13L));
    }
}
