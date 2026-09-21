package com.planit.chat.service;

import com.planit.chat.dto.ChatPolicyConsentResponse;
import com.planit.chat.dto.ChatPolicyResponse;

public interface ChatPolicyService {

    ChatPolicyResponse getCurrentPolicy(String userPublicId);

    ChatPolicyConsentResponse recordConsent(
            String userPublicId,
            String policyVersionId
    );
}
