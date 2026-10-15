package com.planit.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AiPrecheckResponse(
        @JsonProperty("status_code")
        int statusCode,
        Data data
) {

    public record Data(
            @JsonProperty("generated_at")
            Instant generatedAt,
            List<Forecast> forecasts
    ) {
    }

    public record Forecast(
            LocalDate date,
            List<RegionForecast> regions
    ) {
    }

    public record RegionForecast(
            String region,
            @JsonProperty("has_issue")
            boolean hasIssue,
            List<Reason> reasons
    ) {
    }

    public record Reason(
            String category,
            String detail
    ) {
    }
}
