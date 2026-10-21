package com.planit.schedule.ai;

import com.planit.schedule.domain.ScheduleStrategy;

import java.util.List;

public record RecommendedRoute(
        ScheduleStrategy strategy,
        Integer preferenceCount,
        Integer discoveryCount,
        List<List<RecommendedPlace>> days
) {
    public RecommendedRoute {
        days = days.stream()
                .map(List::copyOf)
                .toList();
    }
}
