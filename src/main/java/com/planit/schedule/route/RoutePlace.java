package com.planit.schedule.route;

public record RoutePlace(
        long placeId,
        double latitude,
        double longitude,
        PlaceCategoryGroup categoryGroup
) {
}
