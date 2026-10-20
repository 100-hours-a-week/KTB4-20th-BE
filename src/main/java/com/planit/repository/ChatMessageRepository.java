package com.planit.repository;

import com.planit.domain.ChatMessage;
import com.planit.domain.ChatMessageStatus;
import com.planit.domain.ChatMessageType;
import com.planit.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findBySenderAndClientMessageId(
            User sender,
            UUID clientMessageId
    );

    Optional<ChatMessage> findByIdAndRegionalChatRoom_IdAndStatus(
            Long id,
            Long roomId,
            ChatMessageStatus status
    );

    @Query("""
            SELECT message.id AS messageId,
                   message.clientMessageId AS clientMessageId,
                   message.messageType AS messageType,
                   textMessage.textContent AS textContent,
                   sender.publicId AS senderPublicId,
                   sender.username AS senderUsername,
                   sender.deletedAt AS senderDeletedAt,
                   message.createdAt AS createdAt
            FROM ChatMessage message
            JOIN message.sender sender
            LEFT JOIN TextChatMessage textMessage
                   ON textMessage.chatMessage = message
            WHERE message.regionalChatRoom.id = :roomId
              AND message.status = :status
            ORDER BY message.createdAt DESC, message.id DESC
            """)
    List<ChatMessageHistoryProjection> findLatestHistory(
            @Param("roomId") Long roomId,
            @Param("status") ChatMessageStatus status,
            Pageable pageable
    );

    @Query("""
            SELECT message.id AS messageId,
                   message.clientMessageId AS clientMessageId,
                   message.messageType AS messageType,
                   textMessage.textContent AS textContent,
                   sender.publicId AS senderPublicId,
                   sender.username AS senderUsername,
                   sender.deletedAt AS senderDeletedAt,
                   message.createdAt AS createdAt
            FROM ChatMessage message
            JOIN message.sender sender
            LEFT JOIN TextChatMessage textMessage
                   ON textMessage.chatMessage = message
            WHERE message.regionalChatRoom.id = :roomId
              AND message.status = :status
              AND (message.createdAt < :beforeCreatedAt
                   OR (message.createdAt = :beforeCreatedAt AND message.id < :beforeMessageId))
            ORDER BY message.createdAt DESC, message.id DESC
            """)
    List<ChatMessageHistoryProjection> findOlderHistory(
            @Param("roomId") Long roomId,
            @Param("status") ChatMessageStatus status,
            @Param("beforeCreatedAt") LocalDateTime beforeCreatedAt,
            @Param("beforeMessageId") Long beforeMessageId,
            Pageable pageable
    );

    @Query("""
            SELECT message.id AS messageId,
                   message.clientMessageId AS clientMessageId,
                   message.messageType AS messageType,
                   textMessage.textContent AS textContent,
                   sender.publicId AS senderPublicId,
                   sender.username AS senderUsername,
                   sender.deletedAt AS senderDeletedAt,
                   message.createdAt AS createdAt
            FROM ChatMessage message
            JOIN message.sender sender
            LEFT JOIN TextChatMessage textMessage
                   ON textMessage.chatMessage = message
            WHERE message.regionalChatRoom.id = :roomId
              AND message.status = :status
              AND (message.createdAt > :afterCreatedAt
                   OR (message.createdAt = :afterCreatedAt AND message.id > :afterMessageId))
            ORDER BY message.createdAt ASC, message.id ASC
            """)
    List<ChatMessageHistoryProjection> findNewerHistory(
            @Param("roomId") Long roomId,
            @Param("status") ChatMessageStatus status,
            @Param("afterCreatedAt") LocalDateTime afterCreatedAt,
            @Param("afterMessageId") Long afterMessageId,
            Pageable pageable
    );

    interface ChatMessageHistoryProjection {

        Long getMessageId();

        UUID getClientMessageId();

        ChatMessageType getMessageType();

        String getTextContent();

        UUID getSenderPublicId();

        String getSenderUsername();

        LocalDateTime getSenderDeletedAt();

        LocalDateTime getCreatedAt();
    }
}
