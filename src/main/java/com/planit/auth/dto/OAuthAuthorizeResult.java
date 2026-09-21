package com.planit.auth.dto;

import java.net.URI;

public record OAuthAuthorizeResult(
        URI authorizationUri,
        String state,
        String returnTo
) {
}