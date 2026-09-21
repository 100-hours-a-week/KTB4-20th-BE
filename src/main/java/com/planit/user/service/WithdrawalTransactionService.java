package com.planit.user.service;

import com.planit.domain.OAuthAccount;
import com.planit.domain.OAuthProvider;
import com.planit.domain.RefreshToken;
import com.planit.domain.User;
import com.planit.repository.OAuthAccountRepository;
import com.planit.repository.RefreshTokenRepository;
import com.planit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WithdrawalTransactionService {

    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void withdraw(
            UUID publicId,
            LocalDateTime withdrawnAt
    ) {
        User user = userRepository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new IllegalStateException(
                        "활성 사용자를 찾을 수 없습니다."
                ));
        OAuthAccount oauthAccount = oauthAccountRepository
                .findByUserAndProvider(user, OAuthProvider.KAKAO)
                .orElseThrow(() -> new IllegalStateException(
                        "카카오 연결 정보를 찾을 수 없습니다."
                ));

        user.withdraw(withdrawnAt);

        List<RefreshToken> refreshTokens = refreshTokenRepository
                .findAllByUserAndRevokedAtIsNull(user);
        refreshTokens.forEach(token -> token.revoke(withdrawnAt));

        oauthAccountRepository.delete(oauthAccount);
    }
}
