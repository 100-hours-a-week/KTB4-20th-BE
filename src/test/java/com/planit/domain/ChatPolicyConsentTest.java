package com.planit.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPolicyConsentTest {

    @Test
    void associatesUserWithPolicyVersion() {
        User user = new User(null, UUID.randomUUID(), "플랜잇사용자");
        ChatPolicyVersion policyVersion = new ChatPolicyVersion(
                "2026-09-19",
                "지역 채팅 운영 정책",
                "정책 본문"
        );

        ChatPolicyConsent consent = new ChatPolicyConsent(user, policyVersion);

        assertThat(consent.getUser()).isSameAs(user);
        assertThat(consent.getChatPolicyVersion()).isSameAs(policyVersion);
        assertThat(consent.getConsentedAt()).isNotNull();
    }
}
