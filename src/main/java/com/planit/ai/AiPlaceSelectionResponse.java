package com.planit.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AiPlaceSelectionResponse(
        @JsonProperty("status_code")
        int statusCode,
        Data data
) {

    public record Data(
            List<List<Place>> places,
            List<Route> routes
    ) {
        public Data(List<List<Place>> places) {
            this(places, List.of());
        }
    }

    public record Route(
            Strategy strategy,
            @JsonProperty("preference_count")
            Integer preferenceCount,
            @JsonProperty("discovery_count")
            Integer discoveryCount,
            List<List<Place>> days
    ) {
    }

    public enum Strategy {
        PREFERENCE,
        DIVERSITY
    }

    public record Place(
            String id,
            DisplayName displayName,
            Location location,
            List<String> types,
            double rating,
            int userRatingCount,
            EditorialSummary editorialSummary,
            @JsonProperty("selected_for")
            List<String> selectedFor,
            @JsonProperty("matched_preferences")
            List<String> matchedPreferences
    ) {
    }

    public record DisplayName(
            String text,
            String languageCode
    ) {
    }

    public record Location(
            double latitude,
            double longitude
    ) {
    }

    public record EditorialSummary(
            String text,
            String languageCode
    ) {
    }
}
