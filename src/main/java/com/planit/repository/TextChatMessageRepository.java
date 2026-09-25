package com.planit.repository;

import com.planit.domain.ChatMessage;
import com.planit.domain.TextChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TextChatMessageRepository extends JpaRepository<TextChatMessage, Long> {

    Optional<TextChatMessage> findByChatMessage(ChatMessage chatMessage);
}
