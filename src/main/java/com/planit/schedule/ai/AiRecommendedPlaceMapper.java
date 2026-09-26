package com.planit.schedule.ai;

import com.planit.ai.AiPlaceSelectionResponse;
import com.planit.schedule.route.PlaceCategoryGroup;
import com.planit.schedule.route.RouteCalculationException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.planit.schedule.route.RouteCalculationException.Reason.INVALID_PLACE_RESULT;

@Component
public final class AiRecommendedPlaceMapper {

    private static final int REQUIRED_PLACE_MIN_COUNT = 5;

    public List<RecommendedPlace> map(AiPlaceSelectionResponse response) {
        if (response == null) {
            throw invalid("AI 추천 장소 응답은 성공 상태와 장소 5개 이상을 포함해야 합니다.");
        }
        AiPlaceRecommendationResponse converted =
                new AiPlaceRecommendationResponse(
                        response.statusCode(),
                        response.data() == null
                                ? null
                                : new AiPlaceRecommendationResponse.Data(
                                        response.data().places() == null
                                                ? null
                                                : response.data().places()
                                                .stream()
                                                .map(this::convert)
                                                .toList()
                                )
                );
        return map(converted);
    }

    public List<RecommendedPlace> map(
            AiPlaceRecommendationResponse response
    ) {
        if (response == null
                || response.statusCode() != 200
                || response.data() == null
                || response.data().places() == null
                || response.data().places().size() < REQUIRED_PLACE_MIN_COUNT) {
            throw invalid("AI 추천 장소 응답은 성공 상태와 장소 5개 이상을 포함해야 합니다.");
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

    private AiPlaceRecommendationResponse.Place convert(
            AiPlaceSelectionResponse.Place place
    ) {
        if (place == null) {
            return null;
        }
        return new AiPlaceRecommendationResponse.Place(
                place.id(),
                place.displayName() == null
                        ? null
                        : new AiPlaceRecommendationResponse.DisplayName(
                                place.displayName().text(),
                                place.displayName().languageCode()
                        ),
                place.location() == null
                        ? null
                        : new AiPlaceRecommendationResponse.Location(
                                place.location().latitude(),
                                place.location().longitude()
                        ),
                place.types(),
                place.rating(),
                (long) place.userRatingCount(),
                place.editorialSummary() == null
                        ? null
                        : new AiPlaceRecommendationResponse.EditorialSummary(
                                place.editorialSummary().text(),
                                place.editorialSummary().languageCode()
                        ),
                place.selectedFor(),
                place.matchedPreferences()
        );
    }

    private PlaceCategoryGroup categoryGroup(
            List<String> types,
            List<String> matchedPreferences
    ) {
        Set<String> normalizedTypes = normalize(types);

        // AI는 Google 장소 타입을 이미 5개 버킷(HISTORY_CULTURE, NATURE_HEALING, ACTIVITY,
        // CONVENIENCE_SHOPPING, FOOD)으로 분류해서 matched_preferences로 준다. types는 93개 이상의
        // 세부 태그라 우리가 아는 몇 개만 정확히 일치시킬 수 있어서, AI의 분류(matched_preferences)를
        // 먼저 신뢰하고 types는 FOOD 버킷 안에서 카페·베이커리 구분과 최후 안전망 용도로만 쓴다.
        // matched_preferences는 "NATURE_HEALING"처럼 여러 단어를 합친 값을 줄 수 있어서
        // 정확히 일치하는 원소만 찾는 containsAny 대신 부분 일치로 확인한다.
        Set<String> preferences = normalize(matchedPreferences);
        if (containsAnyPart(preferences, "history_culture", "tourism_culture")) {
            return PlaceCategoryGroup.TOURISM_CULTURE;
        }
        if (containsAnyPart(preferences, "activity", "experience_activity")) {
            return PlaceCategoryGroup.ACTIVITY;
        }
        if (containsAnyPart(preferences, "food", "restaurant")) {
            // FOOD 버킷은 음식점과 카페·베이커리를 함께 묶어서 주므로, 세부 구분은 원본 타입으로 한다.
            return containsAny(normalizedTypes, "cafe", "bakery")
                    ? PlaceCategoryGroup.CAFE_DESSERT
                    : PlaceCategoryGroup.RESTAURANT;
        }
        if (containsAnyPart(preferences, "cafe_dessert")) {
            return PlaceCategoryGroup.CAFE_DESSERT;
        }
        if (containsAnyPart(preferences, "shopping")) {
            return PlaceCategoryGroup.SHOPPING;
        }
        if (containsAnyPart(preferences, "rest", "healing")) {
            return PlaceCategoryGroup.REST;
        }

        // matched_preferences로 분류가 안 되면 원본 타입으로 마지막으로 시도한다.
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

    private boolean containsAnyPart(Set<String> values, String... candidates) {
        for (String value : values) {
            for (String candidate : candidates) {
                if (value.contains(candidate)) {
                    return true;
                }
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
