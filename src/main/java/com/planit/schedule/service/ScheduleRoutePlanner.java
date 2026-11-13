package com.planit.schedule.service;

import com.planit.schedule.ai.RecommendedPlace;
import com.planit.schedule.ai.RecommendedRoute;
import com.planit.schedule.domain.ScheduleStrategy;
import com.planit.schedule.route.RouteCalculationException;
import com.planit.schedule.route.RoutePlace;
import com.planit.schedule.route.RoutePlan;
import com.planit.schedule.route.ShortestRouteCalculator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 추천 경로의 일차별 방문 순서와 이동 구간을 계산한다. 최단 경로 전략은 순서를 다시 정하고,
 * 나머지 전략은 AI가 준 순서를 지킨다. 동기 일정 생성과 메시지 큐 결과 반영이 함께 쓴다.
 */
@Component
public class ScheduleRoutePlanner {

    private final ShortestRouteCalculator calculator = new ShortestRouteCalculator();

    /**
     * @throws RouteCalculationException 조건을 만족하는 경로가 없거나 장소 값이 잘못됐으면
     */
    public List<PlannedRoute> plan(List<RecommendedRoute> routes) {
        List<PlannedRoute> plannedRoutes = new ArrayList<>(routes.size());
        for (RecommendedRoute route : routes) {
            List<RoutePlan> dayPlans = new ArrayList<>(route.days().size());
            for (List<RecommendedPlace> places : route.days()) {
                dayPlans.add(planDay(
                        places,
                        route.strategy() != ScheduleStrategy.SHORTEST
                ));
            }
            plannedRoutes.add(new PlannedRoute(route, dayPlans));
        }
        return List.copyOf(plannedRoutes);
    }

    private RoutePlan planDay(
            List<RecommendedPlace> places,
            boolean preserveOrder
    ) {
        List<RoutePlace> routePlaces = places.stream()
                .map(place -> new RoutePlace(
                        place.googlePlaceId(),
                        place.latitude(),
                        place.longitude(),
                        place.categoryGroup()
                ))
                .toList();
        return preserveOrder
                ? calculator.calculateInGivenOrder(routePlaces)
                : calculator.calculate(routePlaces);
    }
}
