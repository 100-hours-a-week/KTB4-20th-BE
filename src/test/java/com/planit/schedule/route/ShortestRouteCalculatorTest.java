package com.planit.schedule.route;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.planit.schedule.route.PlaceCategoryGroup.ACTIVITY;
import static com.planit.schedule.route.PlaceCategoryGroup.CAFE_DESSERT;
import static com.planit.schedule.route.PlaceCategoryGroup.RESTAURANT;
import static com.planit.schedule.route.PlaceCategoryGroup.SHOPPING;
import static com.planit.schedule.route.PlaceCategoryGroup.TOURISM_CULTURE;
import static com.planit.schedule.route.RouteCalculationException.Reason.INVALID_PLACE_RESULT;
import static com.planit.schedule.route.RouteCalculationException.Reason.ROUTE_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShortestRouteCalculatorTest {

    private final ShortestRouteCalculator calculator =
            new ShortestRouteCalculator();

    @Test
    void findsExactShortestOpenRouteAndBuildsFiveLegs() {
        List<RoutePlace> places = List.of(
                place(4, 0, 3, ACTIVITY),
                place(2, 0, 1, RESTAURANT),
                place(6, 0, 5, SHOPPING),
                place(1, 0, 0, TOURISM_CULTURE),
                place(5, 0, 4, CAFE_DESSERT),
                place(3, 0, 2, TOURISM_CULTURE)
        );

        RoutePlan plan = calculator.calculate(places);

        assertThat(plan.places())
                .extracting(RoutePlace::placeId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(plan.legs()).hasSize(5);
        assertThat(plan.legs())
                .extracting(RouteLeg::order)
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(plan.totalDistanceMeters())
                .isEqualTo(plan.legs().stream()
                        .mapToLong(RouteLeg::distanceMeters)
                        .sum());
    }

    @Test
    void appliesRestaurantCafeAndActivitySequenceConstraints() {
        List<RoutePlace> places = List.of(
                place(1, 35.0, 129.000, RESTAURANT),
                place(2, 35.0, 129.001, RESTAURANT),
                place(3, 35.0, 129.002, CAFE_DESSERT),
                place(4, 35.0, 129.003, ACTIVITY),
                place(5, 35.0, 129.004, ACTIVITY),
                place(6, 35.0, 129.005, ACTIVITY)
        );

        RoutePlan plan = calculator.calculate(places);

        List<PlaceCategoryGroup> groups = plan.places().stream()
                .map(RoutePlace::categoryGroup)
                .toList();
        for (int index = 1; index < groups.size(); index++) {
            PlaceCategoryGroup current = groups.get(index);
            PlaceCategoryGroup previous = groups.get(index - 1);
            assertThat(current == RESTAURANT && previous == RESTAURANT)
                    .isFalse();
            assertThat(current == CAFE_DESSERT && previous == CAFE_DESSERT)
                    .isFalse();
        }
        for (int index = 2; index < groups.size(); index++) {
            boolean threeActivities = groups.get(index) == ACTIVITY
                    && groups.get(index - 1) == ACTIVITY
                    && groups.get(index - 2) == ACTIVITY;
            assertThat(threeActivities).isFalse();
        }
    }

    @Test
    void usesPlaceIdOrderToBreakEqualDistanceTies() {
        List<RoutePlace> places = List.of(
                place(6, 35, 129, SHOPPING),
                place(5, 35, 129, SHOPPING),
                place(4, 35, 129, SHOPPING),
                place(3, 35, 129, SHOPPING),
                place(2, 35, 129, SHOPPING),
                place(1, 35, 129, SHOPPING)
        );

        RoutePlan plan = calculator.calculate(places);

        assertThat(plan.places())
                .extracting(RoutePlace::placeId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(plan.totalDistanceMeters()).isZero();
    }

    @Test
    void calculatesHaversineDistanceInMeters() {
        List<RoutePlace> places = List.of(
                place(1, 0, 0, SHOPPING),
                place(2, 0, 1, SHOPPING),
                place(3, 0, 2, SHOPPING),
                place(4, 0, 3, SHOPPING),
                place(5, 0, 4, SHOPPING),
                place(6, 0, 5, SHOPPING)
        );

        RoutePlan plan = calculator.calculate(places);

        assertThat(plan.legs().getFirst().distanceMeters())
                .isBetween(111_194L, 111_196L);
        assertThat(plan.totalDistanceMeters())
                .isBetween(555_970L, 555_980L);
    }

    @Test
    void rejectsAnythingOtherThanSixPlaces() {
        assertThatThrownBy(() -> calculator.calculate(List.of(
                place(1, 0, 0, SHOPPING)
        )))
                .isInstanceOfSatisfying(
                        RouteCalculationException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(INVALID_PLACE_RESULT)
                );
    }

    @Test
    void rejectsDuplicatePlaceIdsAndInvalidCoordinates() {
        List<RoutePlace> duplicateIds = List.of(
                place(1, 0, 0, SHOPPING),
                place(1, 0, 1, SHOPPING),
                place(3, 0, 2, SHOPPING),
                place(4, 0, 3, SHOPPING),
                place(5, 0, 4, SHOPPING),
                place(6, 0, 5, SHOPPING)
        );
        List<RoutePlace> invalidCoordinate = List.of(
                place(1, 91, 0, SHOPPING),
                place(2, 0, 1, SHOPPING),
                place(3, 0, 2, SHOPPING),
                place(4, 0, 3, SHOPPING),
                place(5, 0, 4, SHOPPING),
                place(6, 0, 5, SHOPPING)
        );

        assertInvalid(duplicateIds);
        assertInvalid(invalidCoordinate);
    }

    @Test
    void failsWhenNoCategoryValidRouteExists() {
        List<RoutePlace> places = List.of(
                place(1, 0, 0, RESTAURANT),
                place(2, 0, 1, RESTAURANT),
                place(3, 0, 2, RESTAURANT),
                place(4, 0, 3, RESTAURANT),
                place(5, 0, 4, RESTAURANT),
                place(6, 0, 5, RESTAURANT)
        );

        assertThatThrownBy(() -> calculator.calculate(places))
                .isInstanceOfSatisfying(
                        RouteCalculationException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(ROUTE_NOT_FOUND)
                );
    }

    private void assertInvalid(List<RoutePlace> places) {
        assertThatThrownBy(() -> calculator.calculate(places))
                .isInstanceOfSatisfying(
                        RouteCalculationException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(INVALID_PLACE_RESULT)
                );
    }

    private RoutePlace place(
            long placeId,
            double latitude,
            double longitude,
            PlaceCategoryGroup categoryGroup
    ) {
        return new RoutePlace(
                placeId,
                latitude,
                longitude,
                categoryGroup
        );
    }
}
