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
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final AuthProperties authProperties;
    private final ReturnToValidator returnToValidator;
    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHasher tokenHasher;
    private final JwtTokenProvider jwtTokenProvider;
    private final UuidV7Generator uuidV7Generator;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final OAuthAccountRepository oauthAccountRepository;
    private final UserRepository userRepository;
    private final ImageFileRepository imageFileRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    public OAuthAuthorizeResult createAuthorizationRequest(
            String returnTo
    ) {
        String validatedReturnTo =
                returnToValidator.validate(returnTo);

        String state = secureTokenGenerator.generate();

        URI authorizationUri = UriComponentsBuilder
                .fromUri(authProperties.kakao().authorizationUri())
                .queryParam(
                        "client_id",
                        authProperties.kakao().clientId()
                )
                .queryParam(
                        "redirect_uri",
                        authProperties.kakao().redirectUri()
                )
                .queryParam("response_type", "code")
                .queryParam("state", state)
                .build()
                .encode()
                .toUri();

        return new OAuthAuthorizeResult(
                authorizationUri,
                state,
                validatedReturnTo
        );
    }
    @Override
    @Transactional
    public OAuthLoginResult login(
            String code,
            String state,
            String oauthError,
            String savedState,
            String savedReturnTo
    ) {
        validateCallback(
                code,
                state,
                oauthError,
                savedState
        );

        String returnTo = returnToValidator.validate(
                savedReturnTo
        );
        String kakaoAccessToken;

        try {
            kakaoAccessToken = kakaoOAuthClient.exchangeCode(code);
        } catch (RestClientException exception) {
            throw new BusinessException(
                    ErrorCode.OAUTH_CODE_EXCHANGE_FAILED,
                    exception
            );
        }

        if (kakaoAccessToken == null
                || kakaoAccessToken.isBlank()) {
            throw new BusinessException(
                    ErrorCode.OAUTH_CODE_EXCHANGE_FAILED
            );
        }

        KakaoUserResponse kakaoUser;

        try {
            kakaoUser = kakaoOAuthClient.getUser(kakaoAccessToken);
        } catch (RestClientException exception) {
            throw new BusinessException(
                    ErrorCode.OAUTH_USER_INFO_FAILED,
                    exception
            );
        }

        if (kakaoUser == null
                || kakaoUser.id() == null
                || kakaoUser.nickname() == null
                || kakaoUser.nickname().isBlank()) {
            throw new BusinessException(
                    ErrorCode.OAUTH_USER_INFO_FAILED
            );
        }

        String nickname = kakaoUser.nickname().trim();
        User user;
        String refreshToken = secureTokenGenerator.generate();
        LocalDateTime issuedAt = LocalDateTime.now();

        try {
            user = findOrCreateUser(kakaoUser, nickname);
            refreshTokenRepository.save(
                    new RefreshToken(
                            user,
                            tokenHasher.sha256(refreshToken),
                            issuedAt,
                            issuedAt.plusDays(30)
                    )
            );
        } catch (DataAccessException exception) {
            throw new BusinessException(
                    ErrorCode.OAUTH_LOGIN_FAILED,
                    exception
            );
        }

        URI redirectUri = authProperties
                .frontendBaseUrl()
                .resolve(returnTo);

        return new OAuthLoginResult(
                redirectUri,
                refreshToken
        );
    }

    private void validateCallback(
            String code,
            String state,
            String oauthError,
            String savedState
    ) {
        if (oauthError != null) {
            throw new BusinessException(
                    ErrorCode.OAUTH_ACCESS_DENIED
            );
        }

        if (state == null
                || state.isBlank()
                || savedState == null
                || savedState.isBlank()
                || !statesMatch(state, savedState)) {
            throw new BusinessException(
                    ErrorCode.OAUTH_STATE_INVALID
            );
        }

        if (code == null
                || code.isBlank()
        ) {
            throw new BusinessException(
                    ErrorCode.OAUTH_CODE_EXCHANGE_FAILED
            );
        }
    }

    private boolean statesMatch(
            String returnedState,
            String savedState
    ) {
        return MessageDigest.isEqual(
                returnedState.getBytes(StandardCharsets.UTF_8),
                savedState.getBytes(StandardCharsets.UTF_8)
        );
    }

    private User findOrCreateUser(
            KakaoUserResponse kakaoUser,
            String nickname
    ) {
        Optional<OAuthAccount> existingAccount =
                oauthAccountRepository
                        .findByProviderAndProviderUserId(
                                OAuthProvider.KAKAO,
                                kakaoUser.providerUserId()
                        );

        if (existingAccount.isPresent()) {
            User user = existingAccount.get().getUser();

            if (user.getDeletedAt() != null) {
                throw new BusinessException(
                        ErrorCode.OAUTH_LOGIN_FAILED
                );
            }

            return user;
        }

        ImageFile defaultProfile = imageFileRepository
                .findByImagePurposeAndDeletedAtIsNull(
                        ImagePurpose.DEFAULT_PROFILE
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.OAUTH_LOGIN_FAILED
                ));

        User user = userRepository.save(
                new User(
                        defaultProfile,
                        uuidV7Generator.generate(),
                        nickname
                )
        );

        oauthAccountRepository.save(
                new OAuthAccount(
                        user,
                        OAuthProvider.KAKAO,
                        kakaoUser.providerUserId()
                )
        );

        return user;
    }

    @Override
    @Transactional
    public AccessTokenResponse refresh(
            String refreshToken,
            String origin
    ) {
        validateOrigin(origin);

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(
                    ErrorCode.REFRESH_TOKEN_REQUIRED
            );
        }

        RefreshToken savedRefreshToken = refreshTokenRepository
                .findByTokenHash(
                        tokenHasher.sha256(refreshToken)
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.REFRESH_TOKEN_INVALID
                ));

        LocalDateTime now = LocalDateTime.now();
        User user = savedRefreshToken.getUser();

        if (savedRefreshToken.getRevokedAt() != null
                || !savedRefreshToken.getExpiresAt().isAfter(now)) {
            throw new BusinessException(
                    ErrorCode.REFRESH_TOKEN_INVALID
            );
        }

        if (user.getDeletedAt() != null
                || user.getPublicId() == null) {
            refreshTokenRepository.revokeByTokenHash(
                    savedRefreshToken.getTokenHash(),
                    now
            );
            throw new BusinessException(
                    ErrorCode.REFRESH_TOKEN_INVALID
            );
        }

        return jwtTokenProvider.issue(user.getPublicId());
    }

    private void validateOrigin(String origin) {
        if (origin == null
                || !authProperties.allowedFrontendOrigin()
                .toString()
                .equals(origin)) {
            throw new BusinessException(ErrorCode.ORIGIN_NOT_ALLOWED);
        }
    }

    @Override
    @Transactional
    public void logout(
            String refreshToken,
            String origin
    ) {
        validateOrigin(origin);

        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        try {
            refreshTokenRepository.findByTokenHash(
                    tokenHasher.sha256(refreshToken)
            ).ifPresent(savedRefreshToken -> {
                LocalDateTime now = LocalDateTime.now();

                if (savedRefreshToken.getRevokedAt() == null
                        && savedRefreshToken
                        .getExpiresAt()
                        .isAfter(now)) {
                    savedRefreshToken.revoke(now);
                    refreshTokenRepository.flush();
                }
            });
        } catch (DataAccessException exception) {
            throw new BusinessException(
                    ErrorCode.LOGOUT_UNAVAILABLE,
                    exception
            );
        }
    }
}
