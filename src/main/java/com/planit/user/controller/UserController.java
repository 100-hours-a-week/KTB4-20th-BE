package com.planit.user.controller;

import com.planit.auth.service.AuthCookieService;
import com.planit.global.response.ApiResponse;
import com.planit.user.dto.CurrentUserResponse;
import com.planit.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthCookieService authCookieService;

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> getCurrentUser(
            Authentication authentication
    ) {
        CurrentUserResponse response =
                userService.getCurrentUser(authentication.getName());

        return ApiResponse.success(
                "CURRENT_USER_RETRIEVED",
                "현재 사용자 정보를 조회했습니다.",
                response
        );
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            Authentication authentication
    ) {
        userService.withdraw(authentication.getName());

        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        authCookieService
                                .deleteRefreshTokenCookie()
                                .toString()
                )
                .build();
    }
}