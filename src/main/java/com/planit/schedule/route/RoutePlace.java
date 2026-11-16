package com.planit.schedule.route;

public record RoutePlace(
        String googlePlaceId,
        double latitude,
        double longitude,
        PlaceCategoryGroup categoryGroup
) {
}
