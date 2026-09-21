package com.planit.repository;

import com.planit.domain.OAuthAccount;
import com.planit.domain.OAuthProvider;
import com.planit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OAuthAccountRepository
        extends JpaRepository<OAuthAccount, Long> {

    Optional<OAuthAccount> findByProviderAndProviderUserId(
            OAuthProvider provider,
            String providerUserId
    );

    Optional<OAuthAccount> findByUserAndProvider(
            User user,
            OAuthProvider provider
    );
}
