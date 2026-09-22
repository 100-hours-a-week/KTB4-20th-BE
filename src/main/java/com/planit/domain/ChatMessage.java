package com.planit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "chat_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_CHAT_MESSAGES_SENDER_CLIENT_MESSAGE",
                columnNames = {"sender_user_id", "client_message_id"}
        )
)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "regional_chat_room_id", nullable = false)
    private RegionalChatRoom regionalChatRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_user_id", nullable = false)
    private User sender;

    @Column(name = "client_message_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    private ChatMessageType messageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChatMessageStatus status;

    @Column(name = "blocked_reason", length = 100)
    private String blockedReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(
            RegionalChatRoom regionalChatRoom,
            User sender,
            UUID clientMessageId,
            ChatMessageType messageType
    ) {
        this.regionalChatRoom = Objects.requireNonNull(regionalChatRoom);
        this.sender = Objects.requireNonNull(sender);
        this.clientMessageId = Objects.requireNonNull(clientMessageId);
        this.messageType = Objects.requireNonNull(messageType);
        this.status = ChatMessageStatus.VISIBLE;
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    public Long getId() {
        return id;
    }

    public RegionalChatRoom getRegionalChatRoom() {
        return regionalChatRoom;
    }

    public User getSender() {
        return sender;
    }

    public UUID getClientMessageId() {
        return clientMessageId;
    }

    public ChatMessageType getMessageType() {
        return messageType;
    }

    public ChatMessageStatus getStatus() {
        return status;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
