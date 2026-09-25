package com.planit.schedule.route;

import java.util.List;

public record RoutePlan(
        List<RoutePlace> places,
        List<RouteLeg> legs,
        long totalDistanceMeters
) {
    public RoutePlan {
        places = List.copyOf(places);
        legs = List.copyOf(legs);
    }
}
