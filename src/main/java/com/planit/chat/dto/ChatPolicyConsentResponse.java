package com.planit.chat.dto;

import java.time.Instant;

public record ChatPolicyConsentResponse(
        String policyVersionId,
        Instant consentedAt
) {
}
