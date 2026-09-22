package com.planit.repository;

import com.planit.domain.ChatPolicyVersion;
import com.planit.domain.ChatPolicyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatPolicyVersionRepository
        extends JpaRepository<ChatPolicyVersion, Long> {

    @Query("""
            SELECT policyVersion
            FROM ChatPolicyVersion policyVersion
            WHERE policyVersion.status = :status
            """)
    Optional<ChatPolicyVersion> findByStatus(@Param("status") ChatPolicyStatus status);
}
