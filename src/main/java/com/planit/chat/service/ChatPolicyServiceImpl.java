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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatPolicyServiceImpl implements ChatPolicyService {

    private final ChatPolicyVersionRepository chatPolicyVersionRepository;
    private final ChatPolicyConsentRepository chatPolicyConsentRepository;
    private final UserRepository userRepository;

    @Override
    public ChatPolicyResponse getCurrentPolicy(String userPublicId) {
        User user = findActiveUser(userPublicId);
        ChatPolicyVersion policyVersion = findCurrentPolicy();

        return chatPolicyConsentRepository.findByUserAndChatPolicyVersion(user, policyVersion)
                .map(consent -> toResponse(policyVersion, consent))
                .orElseGet(() -> toResponse(policyVersion, null));
    }

    @Override
    @Transactional
    public ChatPolicyConsentResponse recordConsent(
            String userPublicId,
            String policyVersionId
    ) {
        User user = findActiveUser(userPublicId);
        ChatPolicyVersion currentPolicy = findCurrentPolicy();
        long requestedPolicyVersionId = parsePolicyVersionId(policyVersionId);

        if (!currentPolicy.getId().equals(requestedPolicyVersionId)) {
            throw new BusinessException(ErrorCode.CHAT_POLICY_VERSION_NOT_ACTIVE);
        }

        ChatPolicyConsent consent = chatPolicyConsentRepository
                .findByUserAndChatPolicyVersion(user, currentPolicy)
                .orElseGet(() -> chatPolicyConsentRepository.save(
                        new ChatPolicyConsent(user, currentPolicy)
                ));

        return new ChatPolicyConsentResponse(
                currentPolicy.getId().toString(),
                consent.getConsentedAt().toInstant(ZoneOffset.UTC)
        );
    }

    private User findActiveUser(String userPublicId) {
        try {
            return userRepository.findByPublicIdAndDeletedAtIsNull(UUID.fromString(userPublicId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, exception);
        }
    }

    private ChatPolicyVersion findCurrentPolicy() {
        return chatPolicyVersionRepository
                .findByStatus(ChatPolicyStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACTIVE_CHAT_POLICY_UNAVAILABLE
                ));
    }

    private long parsePolicyVersionId(String policyVersionId) {
        try {
            return Long.parseLong(policyVersionId);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
        }
    }

    private ChatPolicyResponse toResponse(
            ChatPolicyVersion policyVersion,
            ChatPolicyConsent consent
    ) {
        return new ChatPolicyResponse(
                policyVersion.getId().toString(),
                policyVersion.getVersion(),
                policyVersion.getTitle(),
                policyVersion.getContent(),
                policyVersion.getEffectiveAt().toInstant(ZoneOffset.UTC),
                consent != null,
                consent == null
                        ? null
                        : consent.getConsentedAt().toInstant(ZoneOffset.UTC)
        );
    }
}
