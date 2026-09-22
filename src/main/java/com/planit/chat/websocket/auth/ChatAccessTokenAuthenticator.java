package com.planit.chat.websocket.auth;

import org.springframework.security.core.Authentication;

public interface ChatAccessTokenAuthenticator {

    Authentication authenticate(String accessToken);
}
