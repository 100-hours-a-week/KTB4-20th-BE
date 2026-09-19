package com.planit.repository;

import com.planit.domain.ChatPolicyVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatPolicyVersionRepository
        extends JpaRepository<ChatPolicyVersion, Long> {
}
