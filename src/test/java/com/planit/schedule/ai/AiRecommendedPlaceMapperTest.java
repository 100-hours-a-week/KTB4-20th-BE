package com.planit.schedule.ai;

import com.planit.schedule.route.PlaceCategoryGroup;
import com.planit.schedule.route.RouteCalculationException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.stream.IntStream;

import static com.planit.schedule.route.RouteCalculationException.Reason.INVALID_PLACE_RESULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRecommendedPlaceMapperTest {

    private final AiRecommendedPlaceMapper mapper =
            new AiRecommendedPlaceMapper();

    @Test
    void deserializesProvidedSnakeCaseFields() throws Exception {
        String json = """
                {
                  "status_code": 200,
                  "data": {
                    "places": [{
                      "id": "google-place-id",
                      "displayName": {"text": "경복궁", "languageCode": "ko"},
                      "location": {"latitude": 37.5796, "longitude": 126.9770},
                      "types": ["historical_landmark", "museum"],
                      "rating": 4.6,
                      "userRatingCount": 4820,
                      "editorialSummary": null,
                      "selected_for": ["user_id_1"],
                      "matched_preferences": ["HISTORY_CULTURE"]
                    }]
                  }
                }
                """;

        AiPlaceRecommendationResponse response = JsonMapper.builder()
                .build()
                .readValue(json, AiPlaceRecommendationResponse.class);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.data().places().getFirst().selectedFor())
                .containsExactly("user_id_1");
        assertThat(response.data().places().getFirst().matchedPreferences())
                .containsExactly("HISTORY_CULTURE");
    }

    @Test
    void mapsSixPlacesAndNormalizesCategoryForRouteCalculation() {
        List<AiPlaceRecommendationResponse.Place> places = IntStream
                .rangeClosed(1, 6)
                .mapToObj(index -> place(
                        "place-" + index,
                        "장소 " + index,
                        index % 2 == 0
                                ? List.of("restaurant")
                                : List.of("historical_landmark"),
                        index % 2 == 0
                                ? List.of("FOOD")
                                : List.of("HISTORY_CULTURE")
                ))
                .toList();

        List<RecommendedPlace> result = mapper.map(response(places));

        assertThat(result).hasSize(6);
        assertThat(result.getFirst().googlePlaceId()).isEqualTo("place-1");
        assertThat(result.getFirst().categoryGroup())
                .isEqualTo(PlaceCategoryGroup.TOURISM_CULTURE);
        assertThat(result.get(1).categoryGroup())
                .isEqualTo(PlaceCategoryGroup.RESTAURANT);
        assertThat(result.getFirst().categoryName())
                .isEqualTo("historical_landmark");
    }

    @Test
    void rejectsNonSuccessResponseAndFewerThanFivePlaces() {
        assertInvalid(new AiPlaceRecommendationResponse(
                500,
                new AiPlaceRecommendationResponse.Data(List.of())
        ));
        assertInvalid(response(List.of(place(
                "place-1",
                "경복궁",
                List.of("museum"),
                List.of("HISTORY_CULTURE")
        ))));
    }

    @Test
    void mapsFivePlacesWhenAiFallsShortOfSix() {
        List<AiPlaceRecommendationResponse.Place> places = IntStream
                .rangeClosed(1, 5)
                .mapToObj(index -> place(
                        "place-" + index,
                        "장소 " + index,
                        List.of("historical_landmark"),
                        List.of("HISTORY_CULTURE")
                ))
                .toList();

        List<RecommendedPlace> result = mapper.map(response(places));

        assertThat(result).hasSize(5);
    }

    @Test
    void classifiesCompoundMatchedPreferenceAsRest() {
        AiPlaceRecommendationResponse.Place place = place(
                "place-1",
                "경주타워",
                List.of("point_of_interest"),
                List.of("NATURE_HEALING")
        );

        List<RecommendedPlace> result = mapper.map(response(List.of(
                place,
                place("place-2", "장소2", List.of("historical_landmark"), List.of("HISTORY_CULTURE")),
                place("place-3", "장소3", List.of("historical_landmark"), List.of("HISTORY_CULTURE")),
                place("place-4", "장소4", List.of("historical_landmark"), List.of("HISTORY_CULTURE")),
                place("place-5", "장소5", List.of("historical_landmark"), List.of("HISTORY_CULTURE"))
        )));

        assertThat(result.getFirst().categoryGroup())
                .isEqualTo(PlaceCategoryGroup.REST);
    }

    @Test
    void prefersAiBucketOverTypeWhenSpaIsGroupedAsShopping() {
        // AI는 "spa" 타입을 CONVENIENCE_SHOPPING 버킷으로 분류해서 준다.
        // types만 보면 "spa"가 REST 안전망에 걸리지만, matched_preferences를 우선해야 한다.
        AiPlaceRecommendationResponse.Place place = place(
                "place-1",
                "경주 스파",
                List.of("spa"),
                List.of("CONVENIENCE_SHOPPING")
        );

        List<RecommendedPlace> result = mapper.map(response(List.of(
                place,
                place("place-2", "장소2", List.of("historical_landmark"), List.of("HISTORY_CULTURE")),
                place("place-3", "장소3", List.of("historical_landmark"), List.of("HISTORY_CULTURE")),
                place("place-4", "장소4", List.of("historical_landmark"), List.of("HISTORY_CULTURE")),
                place("place-5", "장소5", List.of("historical_landmark"), List.of("HISTORY_CULTURE"))
        )));

        assertThat(result.getFirst().categoryGroup())
                .isEqualTo(PlaceCategoryGroup.SHOPPING);
    }

    @Test
    void splitsFoodBucketIntoCafeAndRestaurantByType() {
        AiPlaceRecommendationResponse.Place cafe = place(
                "place-1",
                "황남 옥수수빵",
                List.of("bakery"),
                List.of("FOOD")
        );
        AiPlaceRecommendationResponse.Place restaurant = place(
                "place-2",
                "경주역 맛집",
                List.of("seafood_restaurant"),
                List.of("FOOD")
        );

        List<RecommendedPlace> result = mapper.map(response(List.of(
                cafe,
                restaurant,
                place("place-3", "장소3", List.of("historical_landmark"), List.of("HISTORY_CULTURE")),
                place("place-4", "장소4", List.of("historical_landmark"), List.of("HISTORY_CULTURE")),
                place("place-5", "장소5", List.of("historical_landmark"), List.of("HISTORY_CULTURE"))
        )));

        assertThat(result.get(0).categoryGroup())
                .isEqualTo(PlaceCategoryGroup.CAFE_DESSERT);
        assertThat(result.get(1).categoryGroup())
                .isEqualTo(PlaceCategoryGroup.RESTAURANT);
    }

    @Test
    void rejectsDuplicateGooglePlaceIds() {
        AiPlaceRecommendationResponse.Place duplicate = place(
                "same-id",
                "경복궁",
                List.of("museum"),
                List.of("HISTORY_CULTURE")
        );

        assertInvalid(response(List.of(
                duplicate,
                duplicate,
                place("3", "장소3", List.of("museum"), List.of("HISTORY_CULTURE")),
                place("4", "장소4", List.of("museum"), List.of("HISTORY_CULTURE")),
                place("5", "장소5", List.of("museum"), List.of("HISTORY_CULTURE")),
                place("6", "장소6", List.of("museum"), List.of("HISTORY_CULTURE"))
        )));
    }

    private AiPlaceRecommendationResponse response(
            List<AiPlaceRecommendationResponse.Place> places
    ) {
        return new AiPlaceRecommendationResponse(
                200,
                new AiPlaceRecommendationResponse.Data(places)
        );
    }

    private AiPlaceRecommendationResponse.Place place(
            String id,
            String name,
            List<String> types,
            List<String> preferences
    ) {
        return new AiPlaceRecommendationResponse.Place(
                id,
                new AiPlaceRecommendationResponse.DisplayName(name, "ko"),
                new AiPlaceRecommendationResponse.Location(37.5, 127.0),
                types,
                4.5,
                100L,
                null,
                List.of("user-1"),
                preferences
        );
    }

    private void assertInvalid(AiPlaceRecommendationResponse response) {
        assertThatThrownBy(() -> mapper.map(response))
                .isInstanceOfSatisfying(
                        RouteCalculationException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(INVALID_PLACE_RESULT)
                );
    }
}
