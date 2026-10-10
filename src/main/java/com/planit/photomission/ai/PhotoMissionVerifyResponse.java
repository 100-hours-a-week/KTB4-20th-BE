package com.planit.photomission.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public record PhotoMissionVerifyResponse(
        String result,
        String reason,
        @JsonProperty("match_score")
        BigDecimal matchScore,
        @JsonProperty("detected_labels")
        List<DetectedLabel> detectedLabels,
        @JsonProperty("landmark_confidence")
        BigDecimal landmarkConfidence,
        @JsonProperty("retry_hint")
        String retryHint
) {

    public record DetectedLabel(
            String name,
            boolean matched
    ) {
    }
}
