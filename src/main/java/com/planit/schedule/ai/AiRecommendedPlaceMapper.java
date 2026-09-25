package com.planit.schedule.ai;

import com.planit.schedule.route.PlaceCategoryGroup;
import com.planit.schedule.route.RouteCalculationException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.planit.schedule.route.RouteCalculationException.Reason.INVALID_PLACE_RESULT;

public final class AiRecommendedPlaceMapper {

    private static final int REQUIRED_PLACE_COUNT = 6;

    public List<RecommendedPlace> map(
            AiPlaceRecommendationResponse response
    ) {
        if (response == null
                || response.statusCode() != 200
                || response.data() == null
                || response.data().places() == null
                || response.data().places().size() != REQUIRED_PLACE_COUNT) {
            throw invalid("AI 추천 장소 응답은 성공 상태와 장소 6개를 포함해야 합니다.");
        }

        Set<String> googlePlaceIds = new HashSet<>();
        return response.data().places().stream()
                .map(place -> mapPlace(place, googlePlaceIds))
                .toList();
    }

    private RecommendedPlace mapPlace(
            AiPlaceRecommendationResponse.Place place,
            Set<String> googlePlaceIds
    ) {
        if (place == null
                || isBlank(place.id())
                || !googlePlaceIds.add(place.id())
                || place.displayName() == null
                || isBlank(place.displayName().text())
                || place.location() == null) {
            throw invalid("AI 추천 장소의 식별값, 이름과 좌표는 필수입니다.");
        }

        double latitude = place.location().latitude();
        double longitude = place.location().longitude();
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude)
                || longitude < -180 || longitude > 180) {
            throw invalid("AI 추천 장소의 좌표가 유효하지 않습니다.");
        }

        List<String> types = safeList(place.types());
        List<String> matchedPreferences = safeList(
                place.matchedPreferences()
        );
        PlaceCategoryGroup categoryGroup = categoryGroup(
                types,
                matchedPreferences
        );
        String categoryName = types.isEmpty() ? null : types.getFirst();
        String summary = place.editorialSummary() == null
                ? null
                : place.editorialSummary().text();

        return new RecommendedPlace(
                place.id(),
                place.displayName().text().trim(),
                latitude,
                longitude,
                categoryName,
                categoryGroup,
                summary,
                safeList(place.selectedFor()),
                matchedPreferences
        );
    }

    private PlaceCategoryGroup categoryGroup(
            List<String> types,
            List<String> matchedPreferences
    ) {
        Set<String> normalizedTypes = normalize(types);
        if (containsAny(normalizedTypes,
                "restaurant", "food", "meal_takeaway", "meal_delivery")) {
            return PlaceCategoryGroup.RESTAURANT;
        }
        if (containsAny(normalizedTypes, "cafe", "bakery")) {
            return PlaceCategoryGroup.CAFE_DESSERT;
        }
        if (containsAny(normalizedTypes,
                "shopping_mall", "store", "market")) {
            return PlaceCategoryGroup.SHOPPING;
        }

        Set<String> preferences = normalize(matchedPreferences);
        if (containsAny(preferences, "history_culture", "tourism_culture")) {
            return PlaceCategoryGroup.TOURISM_CULTURE;
        }
        if (containsAny(preferences, "activity", "experience_activity")) {
            return PlaceCategoryGroup.ACTIVITY;
        }
        if (containsAny(preferences, "food", "restaurant")) {
            return PlaceCategoryGroup.RESTAURANT;
        }
        if (containsAny(preferences, "cafe_dessert")) {
            return PlaceCategoryGroup.CAFE_DESSERT;
        }
        if (containsAny(preferences, "shopping")) {
            return PlaceCategoryGroup.SHOPPING;
        }
        if (containsAny(preferences, "rest", "healing")) {
            return PlaceCategoryGroup.REST;
        }
        if (containsAny(normalizedTypes,
                "historical_landmark", "museum", "tourist_attraction")) {
            return PlaceCategoryGroup.TOURISM_CULTURE;
        }
        if (containsAny(normalizedTypes,
                "amusement_park", "sports_complex", "tour_agency")) {
            return PlaceCategoryGroup.ACTIVITY;
        }
        if (containsAny(normalizedTypes, "park", "spa", "lodging")) {
            return PlaceCategoryGroup.REST;
        }
        throw invalid("AI 추천 장소의 카테고리를 분류할 수 없습니다.");
    }

    private Set<String> normalize(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean containsAny(Set<String> values, String... candidates) {
        for (String candidate : candidates) {
            if (values.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private RouteCalculationException invalid(String message) {
        return new RouteCalculationException(INVALID_PLACE_RESULT, message);
    }
}
