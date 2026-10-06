package com.planit.user.service;

import com.planit.auth.kakao.KakaoOAuthClient;
import com.planit.domain.ImageFile;
import com.planit.domain.OAuthAccount;
import com.planit.domain.OAuthProvider;
import com.planit.domain.RefreshToken;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.image.storage.ImageStorage;
import com.planit.repository.OAuthAccountRepository;
import com.planit.repository.RefreshTokenRepository;
import com.planit.repository.UserRepository;
import com.planit.trip.service.TripService;
import com.planit.user.dto.CurrentUserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;

class UserServiceImplTest {

    private static final UUID USER_PUBLIC_ID = UUID.fromString(
            "01991f6e-7300-7b21-a3cc-1436db3df95e"
    );
    private static final String PROFILE_IMAGE_KEY =
            "profiles/user/profile.jpg";
    private static final String PROFILE_IMAGE_URL =
            "https://example.com/presigned-profile";

    private UserRepository userRepository;
    private OAuthAccountRepository oauthAccountRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private KakaoOAuthClient kakaoOAuthClient;
    private ImageStorage imageStorage;
    private TripService tripService;
    private WithdrawalTransactionService withdrawalTransactionService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        oauthAccountRepository = mock(OAuthAccountRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        kakaoOAuthClient = mock(KakaoOAuthClient.class);
        imageStorage = mock(ImageStorage.class);
        tripService = mock(TripService.class);
        withdrawalTransactionService =
                new WithdrawalTransactionService(
                        userRepository,
                        oauthAccountRepository,
                        refreshTokenRepository,
                        tripService
                );
        userService = new UserServiceImpl(
                userRepository,
                oauthAccountRepository,
                kakaoOAuthClient,
                withdrawalTransactionService,
                imageStorage
        );
    }

    @DisplayName("현재 활성 사용자 정보를 조회한다")
    @Test
    void getsCurrentActiveUser() {
        ImageFile imageFile = mock(ImageFile.class);
        User user = new User(
                imageFile,
                USER_PUBLIC_ID,
                "플랜잇사용자"
        );
        when(imageFile.getImageKey()).thenReturn(PROFILE_IMAGE_KEY);
        when(imageStorage.createReadUrl(PROFILE_IMAGE_KEY))
                .thenReturn(PROFILE_IMAGE_URL);
        when(userRepository.findByPublicIdAndDeletedAtIsNull(
                USER_PUBLIC_ID
        )).thenReturn(Optional.of(user));

        CurrentUserResponse response = userService.getCurrentUser(
                USER_PUBLIC_ID.toString()
        );

        assertThat(response.publicId()).isEqualTo(USER_PUBLIC_ID);
        assertThat(response.userName()).isEqualTo("플랜잇사용자");
        assertThat(response.profileImageUrl())
                .isEqualTo(PROFILE_IMAGE_URL);
        verify(imageStorage).createReadUrl(PROFILE_IMAGE_KEY);
    }

    @DisplayName("프로필 이미지가 없는 사용자는 이미지 URL을 반환하지 않는다")
    @Test
    void returnsNullProfileImageUrlWithoutProfileImage() {
        User user = new User(
                null,
                USER_PUBLIC_ID,
                "플랜잇사용자"
        );
        when(userRepository.findByPublicIdAndDeletedAtIsNull(
                USER_PUBLIC_ID
        )).thenReturn(Optional.of(user));

        CurrentUserResponse response = userService.getCurrentUser(
                USER_PUBLIC_ID.toString()
        );

        assertThat(response.profileImageUrl()).isNull();
        verifyNoInteractions(imageStorage);
    }

    @DisplayName("존재하지 않거나 탈퇴한 사용자 조회를 거부한다")
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

    @DisplayName("JWT에 담긴 사용자 ID 형식이 올바르지 않으면 거부한다")
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

    @DisplayName("사용자를 탈퇴 처리하고 리프레시 토큰을 폐기한다")
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
        verify(tripService).leaveAllTripsForWithdrawal(
                org.mockito.ArgumentMatchers.eq(user),
                any(LocalDateTime.class)
        );
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

    @DisplayName("카카오 연결 해제에 실패하면 로컬 사용자를 활성 상태로 유지한다")
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

    @DisplayName("로컬 사용자 탈퇴 처리 실패를 오류로 반환한다")
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
                imageStorage
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
