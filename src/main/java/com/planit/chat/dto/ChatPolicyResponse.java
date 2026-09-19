package com.planit.chat.dto;

import java.time.Instant;

public record ChatPolicyResponse(
        String policyVersionId,
        String version,
        String title,
        String content,
        Instant effectiveAt,
        boolean consented,
        Instant consentedAt
) {
}
