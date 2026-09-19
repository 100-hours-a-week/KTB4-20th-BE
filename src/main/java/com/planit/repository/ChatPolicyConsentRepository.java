package com.planit.repository;

import com.planit.domain.ChatPolicyConsent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatPolicyConsentRepository
        extends JpaRepository<ChatPolicyConsent, Long> {
}
