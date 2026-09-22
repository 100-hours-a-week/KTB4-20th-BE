package com.planit.repository;

import com.planit.domain.ChatMessage;
import com.planit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findBySenderAndClientMessageId(
            User sender,
            UUID clientMessageId
    );
}
