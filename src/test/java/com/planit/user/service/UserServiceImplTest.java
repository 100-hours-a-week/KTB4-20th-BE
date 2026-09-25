package com.planit.user.service;

import com.planit.auth.kakao.KakaoOAuthClient;
import com.planit.domain.ImageFile;
import com.planit.domain.OAuthAccount;
import com.planit.domain.OAuthProvider;
import com.planit.domain.RefreshToken;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.image.config.ImageProperties;
import com.planit.repository.OAuthAccountRepository;
import com.planit.repository.RefreshTokenRepository;
import com.planit.repository.UserRepository;
import com.planit.user.dto.CurrentUserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;

class UserServiceImplTest {

    private static final UUID USER_PUBLIC_ID = UUID.fromString(
            "01991f6e-7300-7b21-a3cc-1436db3df95e"
    );
    private static final String DEFAULT_PROFILE_IMAGE_URL =
            "http://localhost:8080/images/default-profile.svg";

    private UserRepository userRepository;
    private OAuthAccountRepository oauthAccountRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private KakaoOAuthClient kakaoOAuthClient;
    private WithdrawalTransactionService withdrawalTransactionService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        oauthAccountRepository = mock(OAuthAccountRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        kakaoOAuthClient = mock(KakaoOAuthClient.class);
        withdrawalTransactionService =
                new WithdrawalTransactionService(
                        userRepository,
                        oauthAccountRepository,
                        refreshTokenRepository
                );
        userService = new UserServiceImpl(
                userRepository,
                oauthAccountRepository,
                kakaoOAuthClient,
                withdrawalTransactionService,
                new ImageProperties(URI.create(DEFAULT_PROFILE_IMAGE_URL))
        );
    }

    @Test
    void getsCurrentActiveUser() {
        User user = new User(
                mock(ImageFile.class),
                USER_PUBLIC_ID,
                "플랜잇사용자"
        );
        when(userRepository.findByPublicIdAndDeletedAtIsNull(
                USER_PUBLIC_ID
        )).thenReturn(Optional.of(user));

        CurrentUserResponse response = userService.getCurrentUser(
                USER_PUBLIC_ID.toString()
        );

        assertThat(response.publicId()).isEqualTo(USER_PUBLIC_ID);
        assertThat(response.userName()).isEqualTo("플랜잇사용자");
        assertThat(response.profileImageUrl())
                .isEqualTo(DEFAULT_PROFILE_IMAGE_URL);
    }

    @Test
    void rejectsUnknownCurrentUser() {
        when(userRepository.findByPublicIdAndDeletedAtIsNull(
                USER_PUBLIC_ID
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getCurrentUser(
                USER_PUBLIC_ID.toString()
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(
                                ErrorCode.AUTHENTICATION_REQUIRED
                        )
                );
    }

    @Test
    void rejectsInvalidJwtSubject() {
        assertThatThrownBy(() -> userService.getCurrentUser(
                "not-a-uuid"
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(
                                ErrorCode.AUTHENTICATION_REQUIRED
                        )
                );
    }

    @Test
    void withdrawsUserAndRevokesRefreshTokens() {
        User user = new User(
                mock(ImageFile.class),
                USER_PUBLIC_ID,
                "플랜잇사용자"
        );
        OAuthAccount oauthAccount = new OAuthAccount(
                user,
                OAuthProvider.KAKAO,
                "123456789"
        );
        LocalDateTime issuedAt = LocalDateTime.now();
        RefreshToken firstToken = new RefreshToken(
                user,
                "first-token-hash",
                issuedAt,
                issuedAt.plusDays(30)
        );
        RefreshToken secondToken = new RefreshToken(
                user,
                "second-token-hash",
                issuedAt,
                issuedAt.plusDays(30)
        );

        when(userRepository.findByPublicIdAndDeletedAtIsNull(
                USER_PUBLIC_ID
        )).thenReturn(Optional.of(user));
        when(oauthAccountRepository.findByUserAndProvider(
                user,
                OAuthProvider.KAKAO
        )).thenReturn(Optional.of(oauthAccount));
        when(refreshTokenRepository.findAllByUserAndRevokedAtIsNull(
                user
        )).thenReturn(List.of(firstToken, secondToken));

        userService.withdraw(USER_PUBLIC_ID.toString());

        verify(kakaoOAuthClient).unlink("123456789");
        verify(oauthAccountRepository).delete(oauthAccount);
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(firstToken.getRevokedAt()).isNotNull();
        assertThat(secondToken.getRevokedAt()).isNotNull();

        org.mockito.InOrder order = inOrder(
                kakaoOAuthClient,
                refreshTokenRepository
        );
        order.verify(kakaoOAuthClient).unlink("123456789");
        order.verify(refreshTokenRepository)
                .findAllByUserAndRevokedAtIsNull(user);
    }

    @Test
    void keepsLocalUserActiveWhenKakaoUnlinkFails() {
        User user = new User(
                mock(ImageFile.class),
                USER_PUBLIC_ID,
                "플랜잇사용자"
        );
        OAuthAccount oauthAccount = new OAuthAccount(
                user,
                OAuthProvider.KAKAO,
                "123456789"
        );
        when(userRepository.findByPublicIdAndDeletedAtIsNull(
                USER_PUBLIC_ID
        )).thenReturn(Optional.of(user));
        when(oauthAccountRepository.findByUserAndProvider(
                user,
                OAuthProvider.KAKAO
        )).thenReturn(Optional.of(oauthAccount));
        doThrow(new RestClientException("Kakao unavailable"))
                .when(kakaoOAuthClient)
                .unlink("123456789");

        assertThatThrownBy(() -> userService.withdraw(
                USER_PUBLIC_ID.toString()
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).extracting(ErrorCode::getCode)
                                .isEqualTo("KAKAO_UNLINK_UNAVAILABLE")
                );

        assertThat(user.getDeletedAt()).isNull();
    }

    @Test
    void reportsLocalWithdrawalFailure() {
        User user = new User(
                mock(ImageFile.class),
                USER_PUBLIC_ID,
                "플랜잇사용자"
        );
        OAuthAccount oauthAccount = new OAuthAccount(
                user,
                OAuthProvider.KAKAO,
                "123456789"
        );
        WithdrawalTransactionService failedTransaction =
                mock(WithdrawalTransactionService.class);
        userService = new UserServiceImpl(
                userRepository,
                oauthAccountRepository,
                kakaoOAuthClient,
                failedTransaction,
                new ImageProperties(URI.create(DEFAULT_PROFILE_IMAGE_URL))
        );
        when(userRepository.findByPublicIdAndDeletedAtIsNull(
                USER_PUBLIC_ID
        )).thenReturn(Optional.of(user));
        when(oauthAccountRepository.findByUserAndProvider(
                user,
                OAuthProvider.KAKAO
        )).thenReturn(Optional.of(oauthAccount));
        org.mockito.Mockito.doThrow(
                new org.springframework.dao
                        .DataAccessResourceFailureException(
                                "DB unavailable"
                        )
        ).when(failedTransaction).withdraw(
                org.mockito.ArgumentMatchers.eq(USER_PUBLIC_ID),
                any(LocalDateTime.class)
        );

        assertThatThrownBy(() -> userService.withdraw(
                USER_PUBLIC_ID.toString()
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(ErrorCode.WITHDRAWAL_UNAVAILABLE)
                );
    }
}
