package com.planit.schedule.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiPlaceRecommendationResponse(
        @JsonProperty("status_code") int statusCode,
        Data data
) {
    public record Data(List<Place> places) {
    }

    public record Place(
            String id,
            DisplayName displayName,
            Location location,
            List<String> types,
            Double rating,
            Long userRatingCount,
            EditorialSummary editorialSummary,
            @JsonProperty("selected_for") List<String> selectedFor,
            @JsonProperty("matched_preferences")
            List<String> matchedPreferences
    ) {
    }

    public record DisplayName(String text, String languageCode) {
    }

    public record Location(double latitude, double longitude) {
    }

    public record EditorialSummary(String text, String languageCode) {
    }
}
