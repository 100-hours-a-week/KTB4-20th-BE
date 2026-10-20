package com.planit.user.service;

import com.planit.auth.kakao.KakaoOAuthClient;
import com.planit.domain.OAuthAccount;
import com.planit.domain.OAuthProvider;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.image.config.ImageProperties;
import com.planit.repository.OAuthAccountRepository;
import com.planit.repository.UserRepository;
import com.planit.user.dto.CurrentUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final WithdrawalTransactionService
            withdrawalTransactionService;
    private final ImageProperties imageProperties;

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(
            String userPublicId
    ) {
        UUID publicId = parsePublicId(userPublicId);
        User user = userRepository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED
                ));

        return new CurrentUserResponse(
                user.getPublicId(),
                user.getUsername(),
                imageProperties.defaultProfileUrl().toString()
        );
    }

    private UUID parsePublicId(String userPublicId) {
        try {
            return UUID.fromString(userPublicId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(
                    ErrorCode.AUTHENTICATION_REQUIRED
            );
        }
    }

    @Override
    public void withdraw(String userPublicId) {
        UUID publicId = parsePublicId(userPublicId);
        User user = userRepository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED
                ));
        OAuthAccount oauthAccount = oauthAccountRepository
                .findByUserAndProvider(user, OAuthProvider.KAKAO)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SERVICE_UNAVAILABLE
                ));

        try {
            kakaoOAuthClient.unlink(oauthAccount.getProviderUserId());
        } catch (RestClientException exception) {
            throw new BusinessException(
                    ErrorCode.KAKAO_UNLINK_UNAVAILABLE,
                    exception
            );
        }

        try {
            withdrawalTransactionService.withdraw(
                    publicId,
                    LocalDateTime.now()
            );
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.WITHDRAWAL_UNAVAILABLE,
                    exception
            );
        }
    }
}
