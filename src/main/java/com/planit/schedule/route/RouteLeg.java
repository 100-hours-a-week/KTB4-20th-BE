package com.planit.schedule.route;

public record RouteLeg(
        long fromPlaceId,
        long toPlaceId,
        int order,
        long distanceMeters
) {
}
