package com.planit.chat.controller;

import com.planit.chat.dto.ChatPolicyConsentResponse;
import com.planit.chat.dto.ChatPolicyResponse;
import com.planit.chat.service.ChatPolicyService;
import com.planit.global.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatPolicyControllerTest {

    private static final String USER_PUBLIC_ID = "01991f6e-7300-7b21-a3cc-1436db3df95e";

    private ChatPolicyService chatPolicyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        chatPolicyService = mock(ChatPolicyService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ChatPolicyController(chatPolicyService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getsCurrentPolicy() throws Exception {
        Instant effectiveAt = Instant.parse("2026-08-31T15:00:00Z");
        when(chatPolicyService.getCurrentPolicy(USER_PUBLIC_ID))
                .thenReturn(new ChatPolicyResponse(
                        "9001",
                        "2026-09-01",
                        "지역 채팅 운영 정책",
                        "정책 본문",
                        effectiveAt,
                        false,
                        null
                ));

        mockMvc.perform(get("/api/chat-policy")
                        .principal(new TestingAuthenticationToken(USER_PUBLIC_ID, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CHAT_POLICY_RETRIEVED"))
                .andExpect(jsonPath("$.data.policyVersionId").value("9001"))
                .andExpect(jsonPath("$.data.effectiveAt").value("2026-08-31T15:00:00Z"))
                .andExpect(jsonPath("$.data.consented").value(false));

        verify(chatPolicyService).getCurrentPolicy(USER_PUBLIC_ID);
    }

    @Test
    void recordsPolicyConsent() throws Exception {
        Instant consentedAt = Instant.parse("2026-09-07T11:00:00.123456Z");
        when(chatPolicyService.recordConsent(USER_PUBLIC_ID, "9001"))
                .thenReturn(new ChatPolicyConsentResponse("9001", consentedAt));

        mockMvc.perform(put("/api/chat-policy/consent")
                        .principal(new TestingAuthenticationToken(USER_PUBLIC_ID, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyVersionId\":\"9001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CHAT_POLICY_CONSENT_RECORDED"))
                .andExpect(jsonPath("$.data.policyVersionId").value("9001"))
                .andExpect(jsonPath("$.data.consentedAt")
                        .value("2026-09-07T11:00:00.123456Z"));

        verify(chatPolicyService).recordConsent(USER_PUBLIC_ID, "9001");
    }

    @Test
    void rejectsInvalidPolicyVersionId() throws Exception {
        mockMvc.perform(put("/api/chat-policy/consent")
                        .principal(new TestingAuthenticationToken(USER_PUBLIC_ID, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"policyVersionId\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("policyVersionId"));
    }
}
