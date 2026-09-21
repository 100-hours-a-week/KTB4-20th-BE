package com.planit.auth.dto;

import java.net.URI;

public record OAuthLoginResult(
        URI redirectUri,
        String refreshToken
) {
}