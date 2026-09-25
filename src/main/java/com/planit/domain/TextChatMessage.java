package com.planit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.Objects;

@Entity
@Table(
        name = "text_chat_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_TEXT_CHAT_MESSAGES_CHAT_MESSAGE",
                columnNames = "chat_message_id"
        )
)
public class TextChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_message_id", nullable = false, unique = true)
    private ChatMessage chatMessage;

    @Column(name = "text_content", nullable = false, length = 1000)
    private String textContent;

    protected TextChatMessage() {
    }

    public TextChatMessage(ChatMessage chatMessage, String textContent) {
        this.chatMessage = Objects.requireNonNull(chatMessage);
        if (chatMessage.getMessageType() != ChatMessageType.TEXT) {
            throw new IllegalArgumentException("텍스트 상세 정보는 TEXT 메시지에만 연결할 수 있습니다.");
        }
        this.textContent = Objects.requireNonNull(textContent);
    }

    public Long getId() {
        return id;
    }

    public ChatMessage getChatMessage() {
        return chatMessage;
    }

    public String getTextContent() {
        return textContent;
    }
}
