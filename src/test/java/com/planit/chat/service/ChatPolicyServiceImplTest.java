package com.planit.chat.service;

import com.planit.chat.dto.ChatPolicyConsentResponse;
import com.planit.chat.dto.ChatPolicyResponse;
import com.planit.domain.ChatPolicyConsent;
import com.planit.domain.ChatPolicyStatus;
import com.planit.domain.ChatPolicyVersion;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.repository.ChatPolicyConsentRepository;
import com.planit.repository.ChatPolicyVersionRepository;
import com.planit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatPolicyServiceImplTest {

    private static final UUID USER_PUBLIC_ID = UUID.fromString(
            "01991f6e-7300-7b21-a3cc-1436db3df95e"
    );

    private ChatPolicyVersionRepository chatPolicyVersionRepository;
    private ChatPolicyConsentRepository chatPolicyConsentRepository;
    private UserRepository userRepository;
    private ChatPolicyServiceImpl chatPolicyService;
    private User user;
    private ChatPolicyVersion policyVersion;

    @BeforeEach
    void setUp() {
        chatPolicyVersionRepository = mock(ChatPolicyVersionRepository.class);
        chatPolicyConsentRepository = mock(ChatPolicyConsentRepository.class);
        userRepository = mock(UserRepository.class);
        chatPolicyService = new ChatPolicyServiceImpl(
                chatPolicyVersionRepository,
                chatPolicyConsentRepository,
                userRepository
        );

        user = new User(null, USER_PUBLIC_ID, "플랜잇사용자");
        policyVersion = new ChatPolicyVersion(
                "2026-09-19",
                "지역 오픈채팅 운영 약관",
                "약관 본문"
        );
        ReflectionTestUtils.setField(policyVersion, "id", 9001L);
        ReflectionTestUtils.setField(policyVersion, "status", ChatPolicyStatus.ACTIVE);
        ReflectionTestUtils.setField(
                policyVersion,
                "effectiveAt",
                LocalDateTime.of(2026, 9, 19, 0, 0)
        );

        when(userRepository.findByPublicIdAndDeletedAtIsNull(USER_PUBLIC_ID))
                .thenReturn(Optional.of(user));
        when(chatPolicyVersionRepository.findByStatus(ChatPolicyStatus.ACTIVE))
                .thenReturn(Optional.of(policyVersion));
    }

    @Test
    void getsCurrentPolicyWithoutConsent() {
        when(chatPolicyConsentRepository.findByUserAndChatPolicyVersion(user, policyVersion))
                .thenReturn(Optional.empty());

        ChatPolicyResponse response = chatPolicyService.getCurrentPolicy(
                USER_PUBLIC_ID.toString()
        );

        assertThat(response.policyVersionId()).isEqualTo("9001");
        assertThat(response.version()).isEqualTo("2026-09-19");
        assertThat(response.consented()).isFalse();
        assertThat(response.consentedAt()).isNull();
    }

    @Test
    void recordsNewConsent() {
        when(chatPolicyConsentRepository.findByUserAndChatPolicyVersion(user, policyVersion))
                .thenReturn(Optional.empty());
        when(chatPolicyConsentRepository.save(any(ChatPolicyConsent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatPolicyConsentResponse response = chatPolicyService.recordConsent(
                USER_PUBLIC_ID.toString(),
                "9001"
        );

        assertThat(response.policyVersionId()).isEqualTo("9001");
        assertThat(response.consentedAt()).isNotNull();
        verify(chatPolicyConsentRepository).save(any(ChatPolicyConsent.class));
    }

    @Test
    void returnsExistingConsentWithoutDuplicateInsert() {
        ChatPolicyConsent existingConsent = new ChatPolicyConsent(user, policyVersion);
        when(chatPolicyConsentRepository.findByUserAndChatPolicyVersion(user, policyVersion))
                .thenReturn(Optional.of(existingConsent));

        ChatPolicyConsentResponse response = chatPolicyService.recordConsent(
                USER_PUBLIC_ID.toString(),
                "9001"
        );

        assertThat(response.consentedAt()).isEqualTo(
                existingConsent.getConsentedAt().toInstant(java.time.ZoneOffset.UTC)
        );
        verify(chatPolicyConsentRepository, never()).save(any());
    }

    @Test
    void rejectsPolicyThatIsNotCurrent() {
        assertThatThrownBy(() -> chatPolicyService.recordConsent(
                USER_PUBLIC_ID.toString(),
                "9002"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_POLICY_VERSION_NOT_ACTIVE);
    }
}
