package com.planit.schedule.route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.planit.schedule.route.RouteCalculationException.Reason.INVALID_PLACE_RESULT;
import static com.planit.schedule.route.RouteCalculationException.Reason.ROUTE_NOT_FOUND;

public final class ShortestRouteCalculator {

    private static final int REQUIRED_PLACE_MIN_COUNT = 5;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    public RoutePlan calculate(List<RoutePlace> places) {
        validate(places);
        int placeCount = places.size();

        List<RoutePlace> orderedInput = places.stream()
                .sorted(Comparator.comparingLong(RoutePlace::placeId))
                .toList();
        SearchResult searchResult = new SearchResult();
        search(
                orderedInput,
                placeCount,
                new boolean[placeCount],
                new ArrayList<>(placeCount),
                searchResult
        );

        if (searchResult.bestRoute == null) {
            throw new RouteCalculationException(
                    ROUTE_NOT_FOUND,
                    "카테고리 배치 조건을 만족하는 동선을 찾을 수 없습니다."
            );
        }

        return toPlan(searchResult.bestRoute);
    }

    private void search(
            List<RoutePlace> places,
            int targetCount,
            boolean[] used,
            List<RoutePlace> route,
            SearchResult result
    ) {
        if (route.size() == targetCount) {
            long distance = totalDistance(route);
            if (distance < result.bestDistance) {
                result.bestDistance = distance;
                result.bestRoute = List.copyOf(route);
            }
            return;
        }

        for (int index = 0; index < places.size(); index++) {
            if (used[index]) {
                continue;
            }

            RoutePlace candidate = places.get(index);
            if (violatesCategoryRule(route, candidate)) {
                continue;
            }

            used[index] = true;
            route.add(candidate);
            search(places, targetCount, used, route, result);
            route.remove(route.size() - 1);
            used[index] = false;
        }
    }

    private boolean violatesCategoryRule(
            List<RoutePlace> route,
            RoutePlace candidate
    ) {
        PlaceCategoryGroup group = candidate.categoryGroup();
        int size = route.size();

        if ((group == PlaceCategoryGroup.RESTAURANT
                || group == PlaceCategoryGroup.CAFE_DESSERT)
                && size >= 1
                && route.get(size - 1).categoryGroup() == group) {
            return true;
        }

        return (group == PlaceCategoryGroup.TOURISM_CULTURE
                || group == PlaceCategoryGroup.ACTIVITY)
                && size >= 2
                && route.get(size - 1).categoryGroup() == group
                && route.get(size - 2).categoryGroup() == group;
    }

    private RoutePlan toPlan(List<RoutePlace> route) {
        List<RouteLeg> legs = new ArrayList<>(route.size() - 1);
        long totalDistance = 0;

        for (int index = 0; index < route.size() - 1; index++) {
            RoutePlace from = route.get(index);
            RoutePlace to = route.get(index + 1);
            long distance = distanceMeters(from, to);
            totalDistance += distance;
            legs.add(new RouteLeg(
                    from.placeId(),
                    to.placeId(),
                    index + 1,
                    distance
            ));
        }

        return new RoutePlan(route, legs, totalDistance);
    }

    private long totalDistance(List<RoutePlace> route) {
        long totalDistance = 0;
        for (int index = 0; index < route.size() - 1; index++) {
            totalDistance += distanceMeters(
                    route.get(index),
                    route.get(index + 1)
            );
        }
        return totalDistance;
    }

    private long distanceMeters(RoutePlace from, RoutePlace to) {
        double fromLatitude = Math.toRadians(from.latitude());
        double toLatitude = Math.toRadians(to.latitude());
        double latitudeDelta = toLatitude - fromLatitude;
        double longitudeDelta = Math.toRadians(
                to.longitude() - from.longitude()
        );

        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(fromLatitude)
                * Math.cos(toLatitude)
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        double boundedHaversine = Math.min(1.0, Math.max(0.0, haversine));
        double centralAngle = 2 * Math.atan2(
                Math.sqrt(boundedHaversine),
                Math.sqrt(1 - boundedHaversine)
        );
        return Math.round(EARTH_RADIUS_METERS * centralAngle);
    }

    private void validate(List<RoutePlace> places) {
        if (places == null || places.size() < REQUIRED_PLACE_MIN_COUNT) {
            throw invalid("장소는 5개 이상이어야 합니다.");
        }

        Set<Long> placeIds = new HashSet<>();
        for (RoutePlace place : places) {
            if (place == null) {
                throw invalid("장소는 null일 수 없습니다.");
            }
            if (place.placeId() <= 0 || !placeIds.add(place.placeId())) {
                throw invalid("장소 ID는 양수이며 중복되지 않아야 합니다.");
            }
            if (!Double.isFinite(place.latitude())
                    || place.latitude() < -90
                    || place.latitude() > 90
                    || !Double.isFinite(place.longitude())
                    || place.longitude() < -180
                    || place.longitude() > 180) {
                throw invalid("장소 좌표가 유효하지 않습니다.");
            }
            if (place.categoryGroup() == null) {
                throw invalid("장소 카테고리 그룹은 필수입니다.");
            }
        }
    }

    private RouteCalculationException invalid(String message) {
        return new RouteCalculationException(INVALID_PLACE_RESULT, message);
    }

    private static final class SearchResult {
        private long bestDistance = Long.MAX_VALUE;
        private List<RoutePlace> bestRoute;
    }
}
