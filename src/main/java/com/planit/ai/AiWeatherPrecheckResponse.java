package com.planit.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiWeatherPrecheckResponse(
        @JsonProperty("status_code")
        int statusCode,
        Data data
) {

    public record Data(
            @JsonProperty("has_issue")
            boolean hasIssue,
            @JsonProperty("affected_place_ids")
            List<String> affectedPlaceIds,
            List<Reason> reasons
    ) {
    }

    public record Reason(
            String category,
            String detail
    ) {
    }
}
