package com.planit.schedule.ai;

import com.planit.schedule.route.PlaceCategoryGroup;

import java.util.List;

public record RecommendedPlace(
        String googlePlaceId,
        String name,
        double latitude,
        double longitude,
        String categoryName,
        PlaceCategoryGroup categoryGroup,
        String editorialSummary,
        List<String> selectedFor,
        List<String> matchedPreferences
) {
    public RecommendedPlace {
        selectedFor = List.copyOf(selectedFor);
        matchedPreferences = List.copyOf(matchedPreferences);
    }
}
