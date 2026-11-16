package com.planit.schedule.route;

public record RouteLeg(
        String fromGooglePlaceId,
        String toGooglePlaceId,
        int order,
        long distanceMeters
) {
}
