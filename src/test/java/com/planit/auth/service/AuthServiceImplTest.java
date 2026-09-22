package com.planit.auth.service;

import com.planit.auth.config.AuthProperties;
import com.planit.auth.dto.AccessTokenResponse;
import com.planit.auth.dto.OAuthAuthorizeResult;
import com.planit.auth.dto.OAuthLoginResult;
import com.planit.auth.id.UuidV7Generator;
import com.planit.auth.kakao.KakaoOAuthClient;
import com.planit.auth.kakao.KakaoUserResponse;
import com.planit.auth.oauth.ReturnToValidator;
import com.planit.auth.token.SecureTokenGenerator;
import com.planit.auth.token.JwtTokenProvider;
import com.planit.auth.token.TokenHasher;
import com.planit.domain.ImageFile;
import com.planit.domain.ImagePurpose;
import com.planit.domain.OAuthAccount;
import com.planit.domain.OAuthProvider;
import com.planit.domain.RefreshToken;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.repository.ImageFileRepository;
import com.planit.repository.OAuthAccountRepository;
import com.planit.repository.RefreshTokenRepository;
import com.planit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

class AuthServiceImplTest {

    private AuthProperties authProperties;
    private KakaoOAuthClient kakaoOAuthClient;
    private OAuthAccountRepository oauthAccountRepository;
    private UserRepository userRepository;
    private ImageFileRepository imageFileRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private TokenHasher tokenHasher;
    private JwtTokenProvider jwtTokenProvider;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authProperties = createAuthProperties();
        kakaoOAuthClient = mock(KakaoOAuthClient.class);
        oauthAccountRepository = mock(
                OAuthAccountRepository.class
        );
        userRepository = mock(UserRepository.class);
        imageFileRepository = mock(ImageFileRepository.class);
        refreshTokenRepository = mock(
                RefreshTokenRepository.class
        );
        tokenHasher = new TokenHasher();
        jwtTokenProvider = mock(JwtTokenProvider.class);

        authService = new AuthServiceImpl(
                authProperties,
                new ReturnToValidator(),
                new SecureTokenGenerator(),
                tokenHasher,
                jwtTokenProvider,
                new UuidV7Generator(),
                kakaoOAuthClient,
                oauthAccountRepository,
                userRepository,
                imageFileRepository,
                refreshTokenRepository
        );
    }

    @Test
    void issuesAccessTokenWithValidRefreshToken() {
        String rawRefreshToken = "refresh-token";
        User user = new User(
                mock(ImageFile.class),
                UUID.fromString(
                        "01991f6e-7300-7b21-a3cc-1436db3df95e"
                ),
                "플랜잇사용자"
        );
        RefreshToken savedRefreshToken = new RefreshToken(
                user,
                tokenHasher.sha256(rawRefreshToken),
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusDays(30)
        );
        AccessTokenResponse expected = new AccessTokenResponse(
                "access-token",
                "Bearer",
                900
        );

        when(refreshTokenRepository.findByTokenHash(
                tokenHasher.sha256(rawRefreshToken)
        )).thenReturn(Optional.of(savedRefreshToken));
        when(jwtTokenProvider.issue(user.getPublicId()))
                .thenReturn(expected);

        AccessTokenResponse result = authService.refresh(
                rawRefreshToken,
                "http://localhost:5173"
        );

        assertThat(result).isEqualTo(expected);
        verify(jwtTokenProvider).issue(user.getPublicId());
    }

    @Test
    void rejectsRefreshFromUnknownOrigin() {
        assertThatThrownBy(() -> authService.refresh(
                "refresh-token",
                "https://unknown.example"
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).extracting(ErrorCode::getCode)
                                .isEqualTo("ORIGIN_NOT_ALLOWED")
                );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void rejectsRefreshWithoutRefreshToken() {
        assertThatThrownBy(() -> authService.refresh(
                null,
                "http://localhost:5173"
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode().getCode()
                        ).isEqualTo("REFRESH_TOKEN_REQUIRED")
                );
    }

    @Test
    void rejectsExpiredRefreshToken() {
        String rawRefreshToken = "refresh-token";
        User user = new User(
                mock(ImageFile.class),
                UUID.fromString(
                        "01991f6e-7300-7b21-a3cc-1436db3df95e"
                ),
                "플랜잇사용자"
        );
        RefreshToken expiredRefreshToken = new RefreshToken(
                user,
                tokenHasher.sha256(rawRefreshToken),
                LocalDateTime.now().minusDays(31),
                LocalDateTime.now().minusDays(1)
        );

        when(refreshTokenRepository.findByTokenHash(
                tokenHasher.sha256(rawRefreshToken)
        )).thenReturn(Optional.of(expiredRefreshToken));

        assertThatThrownBy(() -> authService.refresh(
                rawRefreshToken,
                "http://localhost:5173"
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(
                                ErrorCode.REFRESH_TOKEN_INVALID
                        )
                );

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void revokesRefreshTokenBelongingToWithdrawnUser() {
        String rawRefreshToken = "refresh-token";
        User user = new User(
                mock(ImageFile.class),
                UUID.fromString(
                        "01991f6e-7300-7b21-a3cc-1436db3df95e"
                ),
                "플랜잇사용자"
        );
        user.withdraw(LocalDateTime.now().minusMinutes(1));
        RefreshToken savedRefreshToken = new RefreshToken(
                user,
                tokenHasher.sha256(rawRefreshToken),
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(29)
        );
        when(refreshTokenRepository.findByTokenHash(
                tokenHasher.sha256(rawRefreshToken)
        )).thenReturn(Optional.of(savedRefreshToken));

        assertThatThrownBy(() -> authService.refresh(
                rawRefreshToken,
                "http://localhost:5173"
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID)
                );

        verify(refreshTokenRepository).revokeByTokenHash(
                org.mockito.ArgumentMatchers.eq(
                        tokenHasher.sha256(rawRefreshToken)
                ),
                any(LocalDateTime.class)
        );
    }

    @Test
    void revokesRefreshTokenOnLogout() {
        String rawRefreshToken = "refresh-token";
        RefreshToken savedRefreshToken = new RefreshToken(
                mock(User.class),
                tokenHasher.sha256(rawRefreshToken),
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusDays(30)
        );

        when(refreshTokenRepository.findByTokenHash(
                tokenHasher.sha256(rawRefreshToken)
        )).thenReturn(Optional.of(savedRefreshToken));

        authService.logout(
                rawRefreshToken,
                "http://localhost:5173"
        );

        assertThat(savedRefreshToken.getRevokedAt())
                .isNotNull();
    }

    @Test
    void succeedsLogoutWithoutRefreshToken() {
        authService.logout(
                null,
                "http://localhost:5173"
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void reportsLogoutDatabaseFailure() {
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenThrow(new org.springframework.dao
                        .DataAccessResourceFailureException(
                                "DB unavailable"
                        ));

        assertThatThrownBy(() -> authService.logout(
                "refresh-token",
                "http://localhost:5173"
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(ErrorCode.LOGOUT_UNAVAILABLE)
                );
    }

    @Test
    void createsKakaoAuthorizationRequest() {
        OAuthAuthorizeResult result =
                authService.createAuthorizationRequest("/");

        UriComponents uri = UriComponentsBuilder
                .fromUri(result.authorizationUri())
                .build();

        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("kauth.kakao.com");
        assertThat(uri.getPath()).isEqualTo("/oauth/authorize");
        assertThat(uri.getQueryParams().getFirst("client_id"))
                .isEqualTo("test-client-id");
        assertThat(uri.getQueryParams().getFirst("redirect_uri"))
                .isEqualTo(
                        "http://localhost:8080/api/auth/oauth/callback"
                );
        assertThat(uri.getQueryParams().getFirst("response_type"))
                .isEqualTo("code");
        assertThat(uri.getQueryParams().getFirst("state"))
                .isEqualTo(result.state());
        assertThat(result.state())
                .matches("[A-Za-z0-9_-]{43}");
        assertThat(result.returnTo()).isEqualTo("/");
    }

    @Test
    void rejectsCallbackWhenStateDoesNotMatch() {
        assertThatThrownBy(() -> authService.login(
                "test-code",
                "returned-state",
                null,
                "saved-state",
                "/"
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).extracting(ErrorCode::getCode)
                                .isEqualTo("OAUTH_STATE_INVALID")
                );

        verifyNoInteractions(kakaoOAuthClient);
    }

    @Test
    void reportsKakaoCodeExchangeFailure() {
        when(kakaoOAuthClient.exchangeCode("test-code"))
                .thenThrow(new org.springframework.web.client
                        .RestClientException("Kakao unavailable"));

        assertThatThrownBy(() -> authService.login(
                "test-code",
                "same-state",
                null,
                "same-state",
                "/"
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode().getCode()
                        ).isEqualTo("OAUTH_CODE_EXCHANGE_FAILED")
                );
    }

    @Test
    void logsInExistingKakaoUser() {
        User user = new User(
                mock(ImageFile.class),
                UUID.fromString(
                        "01991f6e-7300-7b21-a3cc-1436db3df95e"
                ),
                "기존사용자"
        );
        OAuthAccount oauthAccount = new OAuthAccount(
                user,
                OAuthProvider.KAKAO,
                "123456789"
        );
        prepareKakaoUser("플랜잇사용자");
        when(oauthAccountRepository
                .findByProviderAndProviderUserId(
                        OAuthProvider.KAKAO,
                        "123456789"
                ))
                .thenReturn(Optional.of(oauthAccount));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OAuthLoginResult result = authService.login(
                "test-code",
                "same-state",
                null,
                "same-state",
                "/"
        );

        ArgumentCaptor<RefreshToken> tokenCaptor =
                ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository)
                .save(tokenCaptor.capture());
        RefreshToken savedToken = tokenCaptor.getValue();

        assertThat(result.redirectUri())
                .isEqualTo(URI.create("http://localhost:5173/"));
        assertThat(result.refreshToken())
                .matches("[A-Za-z0-9_-]{43}");
        assertThat(savedToken.getUser()).isSameAs(user);
        assertThat(savedToken.getTokenHash())
                .isEqualTo(tokenHasher.sha256(
                        result.refreshToken()
                ));
        assertThat(savedToken.getExpiresAt())
                .isEqualTo(savedToken.getIssuedAt().plusDays(30));
    }

    @Test
    void createsUserForNewKakaoAccount() {
        ImageFile defaultProfile = mock(ImageFile.class);
        prepareKakaoUser("플랜잇사용자");
        when(oauthAccountRepository
                .findByProviderAndProviderUserId(
                        OAuthProvider.KAKAO,
                        "123456789"
                ))
                .thenReturn(Optional.empty());
        when(imageFileRepository
                .findByImagePurposeAndDeletedAtIsNull(
                        ImagePurpose.DEFAULT_PROFILE
                ))
                .thenReturn(Optional.of(defaultProfile));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(oauthAccountRepository.save(any(OAuthAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OAuthLoginResult result = authService.login(
                "test-code",
                "same-state",
                null,
                "same-state",
                "/invitations/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<OAuthAccount> accountCaptor =
                ArgumentCaptor.forClass(OAuthAccount.class);
        verify(userRepository).save(userCaptor.capture());
        verify(oauthAccountRepository)
                .save(accountCaptor.capture());

        User savedUser = userCaptor.getValue();
        OAuthAccount savedAccount = accountCaptor.getValue();

        assertThat(result.redirectUri()).isEqualTo(URI.create(
                "http://localhost:5173/invitations/"
                        + "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        ));
        assertThat(savedUser.getImageFile())
                .isSameAs(defaultProfile);
        assertThat(savedUser.getPublicId().version())
                .isEqualTo(7);
        assertThat(savedUser.getUsername())
                .isEqualTo("플랜잇사용자");
        assertThat(savedAccount.getUser())
                .isSameAs(savedUser);
        assertThat(savedAccount.getProvider())
                .isEqualTo(OAuthProvider.KAKAO);
        assertThat(savedAccount.getProviderUserId())
                .isEqualTo("123456789");
    }

    @Test
    void trimsKakaoNicknameBeforeSavingUser() {
        ImageFile defaultProfile = mock(ImageFile.class);
        prepareKakaoUser("  플랜잇사용자  ");
        when(oauthAccountRepository
                .findByProviderAndProviderUserId(
                        OAuthProvider.KAKAO,
                        "123456789"
                ))
                .thenReturn(Optional.empty());
        when(imageFileRepository
                .findByImagePurposeAndDeletedAtIsNull(
                        ImagePurpose.DEFAULT_PROFILE
                ))
                .thenReturn(Optional.of(defaultProfile));
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(oauthAccountRepository.save(any(OAuthAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.login(
                "test-code",
                "same-state",
                null,
                "same-state",
                "/"
        );

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername())
                .isEqualTo("플랜잇사용자");
    }

    @Test
    void rejectsKakaoUserWithoutNickname() {
        prepareKakaoUser(null);
        when(imageFileRepository
                .findByImagePurposeAndDeletedAtIsNull(
                        ImagePurpose.DEFAULT_PROFILE
                ))
                .thenReturn(Optional.of(mock(ImageFile.class)));

        assertThatThrownBy(() -> authService.login(
                "test-code",
                "same-state",
                null,
                "same-state",
                "/"
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).extracting(ErrorCode::getCode)
                                .isEqualTo("OAUTH_USER_INFO_FAILED")
                );
    }

    private void prepareKakaoUser(String nickname) {
        when(kakaoOAuthClient.exchangeCode("test-code"))
                .thenReturn("kakao-access-token");
        when(kakaoOAuthClient.getUser("kakao-access-token"))
                .thenReturn(new KakaoUserResponse(
                        123456789L,
                        new KakaoUserResponse.KakaoAccount(
                                new KakaoUserResponse.Profile(
                                        nickname
                                )
                        )
                ));
    }

    private AuthProperties createAuthProperties() {
        return new AuthProperties(
                URI.create("http://localhost:5173"),
                URI.create("http://localhost:5173"),
                false,
                "planit_oauth_state",
                Duration.ofMinutes(10),
                new AuthProperties.Jwt(
                        "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=",
                        "planit-auth",
                        "planit-api",
                        Duration.ofMinutes(15)
                ),
                new AuthProperties.Kakao(
                        "test-client-id",
                        "test-client-secret",
                        "test-admin-key",
                        URI.create(
                                "http://localhost:8080"
                                        + "/api/auth/oauth/callback"
                        ),
                        URI.create(
                                "https://kauth.kakao.com"
                                        + "/oauth/authorize"
                        ),
                        URI.create(
                                "https://kauth.kakao.com"
                                        + "/oauth/token"
                        ),
                        URI.create(
                                "https://kapi.kakao.com"
                                        + "/v2/user/me"
                        ),
                        URI.create(
                                "https://kapi.kakao.com"
                                        + "/v1/user/unlink"
                        )
                )
        );
    }
}
