package com.planit.photomission.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PhotoMissionGenerateRequest(
        @JsonProperty("itinerary_id")
        String itineraryId,
        List<Place> places
) {
    public record Place(
            String id,
            DisplayName displayName,
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
}
