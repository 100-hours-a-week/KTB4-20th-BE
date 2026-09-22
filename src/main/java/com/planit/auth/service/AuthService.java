package com.planit.auth.service;

import com.planit.auth.dto.AccessTokenResponse;
import com.planit.auth.dto.OAuthAuthorizeResult;
import com.planit.auth.dto.OAuthLoginResult;

public interface AuthService {

    OAuthAuthorizeResult createAuthorizationRequest(String returnTo);

    OAuthLoginResult login(
            String code,
            String state,
            String oauthError,
            String savedState,
            String savedReturnTo
    );

    AccessTokenResponse refresh(
            String refreshToken,
            String origin
    );

    void logout(
            String refreshToken,
            String origin
    );
}