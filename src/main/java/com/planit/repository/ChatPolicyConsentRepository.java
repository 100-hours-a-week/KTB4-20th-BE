package com.planit.repository;

import com.planit.domain.ChatPolicyConsent;
import com.planit.domain.ChatPolicyVersion;
import com.planit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatPolicyConsentRepository
        extends JpaRepository<ChatPolicyConsent, Long> {

    Optional<ChatPolicyConsent> findByUserAndChatPolicyVersion(
            User user,
            ChatPolicyVersion chatPolicyVersion
    );
}
