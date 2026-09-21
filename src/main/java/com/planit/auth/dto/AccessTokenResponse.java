package com.planit.auth.dto;

public record AccessTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}