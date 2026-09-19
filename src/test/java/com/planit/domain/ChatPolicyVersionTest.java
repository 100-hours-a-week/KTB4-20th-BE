package com.planit.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPolicyVersionTest {

    @Test
    void createsDraftPolicyVersion() {
        ChatPolicyVersion policyVersion = new ChatPolicyVersion(
                "2026-09-19",
                "지역 채팅 운영 정책",
                "정책 본문"
        );

        assertThat(policyVersion.getVersion()).isEqualTo("2026-09-19");
        assertThat(policyVersion.getTitle()).isEqualTo("지역 채팅 운영 정책");
        assertThat(policyVersion.getContent()).isEqualTo("정책 본문");
        assertThat(policyVersion.getStatus()).isEqualTo(ChatPolicyStatus.DRAFT);
        assertThat(policyVersion.getCreatedAt()).isNotNull();
        assertThat(policyVersion.getUpdatedAt()).isEqualTo(policyVersion.getCreatedAt());
    }
}
