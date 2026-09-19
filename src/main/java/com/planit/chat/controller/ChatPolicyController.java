package com.planit.chat.controller;

import com.planit.chat.dto.ChatPolicyConsentRequest;
import com.planit.chat.dto.ChatPolicyConsentResponse;
import com.planit.chat.dto.ChatPolicyResponse;
import com.planit.chat.service.ChatPolicyService;
import com.planit.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat-policy")
@RequiredArgsConstructor
public class ChatPolicyController {

    private final ChatPolicyService chatPolicyService;

    @GetMapping
    public ApiResponse<ChatPolicyResponse> getCurrentPolicy(
            Authentication authentication
    ) {
        ChatPolicyResponse response = chatPolicyService.getCurrentPolicy(
                authentication.getName()
        );

        return ApiResponse.success(
                "CHAT_POLICY_RETRIEVED",
                "현재 채팅 운영 정책을 조회했습니다.",
                response
        );
    }

    @PutMapping("/consent")
    public ApiResponse<ChatPolicyConsentResponse> consentToPolicy(
            Authentication authentication,
            @Valid @RequestBody ChatPolicyConsentRequest request
    ) {
        ChatPolicyConsentResponse response = chatPolicyService.recordConsent(
                authentication.getName(),
                request.policyVersionId()
        );

        return ApiResponse.success(
                "CHAT_POLICY_CONSENT_RECORDED",
                "채팅 운영 정책에 동의했습니다.",
                response
        );
    }
}
