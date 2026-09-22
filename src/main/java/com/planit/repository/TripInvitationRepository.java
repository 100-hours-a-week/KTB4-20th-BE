package com.planit.repository;

import com.planit.domain.TripInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TripInvitationRepository
        extends JpaRepository<TripInvitation, Long> {

    Optional<TripInvitation> findByTokenHash(
            String tokenHash
    );
}