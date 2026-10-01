package com.planit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ChatMessageTest {

    @DisplayName("클라이언트 메시지 ID를 가진 공개 텍스트 메시지를 생성한다")
    @Test
    void createsVisibleTextMessageWithClientMessageId() {
        RegionalChatRoom room = mock(RegionalChatRoom.class);
        User sender = mock(User.class);
        UUID clientMessageId = UUID.randomUUID();

        ChatMessage message = new ChatMessage(
                room,
                sender,
                clientMessageId,
                ChatMessageType.TEXT
        );

        assertThat(message.getRegionalChatRoom()).isSameAs(room);
        assertThat(message.getSender()).isSameAs(sender);
        assertThat(message.getClientMessageId()).isEqualTo(clientMessageId);
        assertThat(message.getMessageType()).isEqualTo(ChatMessageType.TEXT);
        assertThat(message.getStatus()).isEqualTo(ChatMessageStatus.VISIBLE);
        assertThat(message.getBlockedReason()).isNull();
        assertThat(message.getCreatedAt()).isNotNull();
    }

    @DisplayName("텍스트 상세 정보는 텍스트 메시지만 참조할 수 있다")
    @Test
    void textDetailsCanOnlyReferenceTextMessages() {
        ChatMessage imageMessage = new ChatMessage(
                mock(RegionalChatRoom.class),
                mock(User.class),
                UUID.randomUUID(),
                ChatMessageType.IMAGE
        );

        assertThatThrownBy(() -> new TextChatMessage(imageMessage, "본문"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("텍스트 메시지와 상세 정보를 연결한다")
    @Test
    void associatesTextDetailsWithTextMessage() {
        ChatMessage message = new ChatMessage(
                mock(RegionalChatRoom.class),
                mock(User.class),
                UUID.randomUUID(),
                ChatMessageType.TEXT
        );

        TextChatMessage textMessage = new TextChatMessage(message, "경주 맛집 추천해주세요.");

        assertThat(textMessage.getChatMessage()).isSameAs(message);
        assertThat(textMessage.getTextContent()).isEqualTo("경주 맛집 추천해주세요.");
    }
}
