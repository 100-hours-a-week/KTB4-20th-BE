package com.planit.schedule.service;

import com.planit.schedule.ai.RecommendedRoute;
import com.planit.schedule.route.RoutePlan;

import java.util.List;

public record PlannedRoute(
        RecommendedRoute route,
        List<RoutePlan> dayPlans
) {
    public PlannedRoute {
        dayPlans = List.copyOf(dayPlans);
    }
}
