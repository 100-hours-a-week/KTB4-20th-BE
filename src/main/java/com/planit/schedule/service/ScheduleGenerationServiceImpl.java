package com.planit.schedule.service;

import com.planit.ai.AiPlaceSelectionResponse;
import com.planit.ai.AiTripClient;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.schedule.domain.ScheduleGenerationReason;
import com.planit.schedule.domain.ScheduleDayPlacePolicy;
import com.planit.schedule.domain.ScheduleStrategy;
import com.planit.schedule.dto.SchedulePlaceSelectionResponse;
import com.planit.schedule.ai.AiRecommendedPlaceMapper;
import com.planit.schedule.ai.RecommendedPlace;
import com.planit.schedule.ai.RecommendedRoute;
import com.planit.schedule.route.RouteCalculationException;
import com.planit.schedule.route.RoutePlace;
import com.planit.schedule.route.RoutePlan;
import com.planit.schedule.route.ShortestRouteCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ScheduleGenerationServiceImpl
        implements ScheduleGenerationService {

    private final ScheduleGenerationPreparationService preparationService;
    private final AiTripClient aiTripClient;
    private final AiRecommendedPlaceMapper recommendedPlaceMapper;
    private final PlacePersistenceService placePersistenceService;
    private final SchedulePersistenceService schedulePersistenceService;
    private final ShortestRouteCalculator calculator =
            new ShortestRouteCalculator();

    @Override
    public SchedulePlaceSelectionResponse generate(
            String userPublicId,
            Long tripId,
            ScheduleGenerationReason reason
    ) {
        ScheduleGenerationPreparation preparation = preparationService.prepare(
                userPublicId,
                tripId,
                reason
        );

        AiPlaceSelectionResponse response;
        try {
            response = aiTripClient.selectPlaces(preparation.request());
        } catch (RestClientException exception) {
            throw new BusinessException(
                    ErrorCode.AI_SCHEDULE_GENERATION_FAILED,
                    exception
            );
        }

        if (!isValidResponse(response, preparation.tripDayCount())) {
            throw new BusinessException(
                    ErrorCode.AI_SCHEDULE_GENERATION_FAILED
            );
        }

        try {
            List<RecommendedRoute> routes = recommendedPlaceMapper.mapRoutes(
                    response
            );
            List<PlannedRoute> plannedRoutes = planRoutes(routes);
            Map<String, Long> placeIds = placePersistenceService
                    .upsertAndFindIds(
                            preparation.regionId(),
                            routes.stream()
                                    .flatMap(route -> route.days().stream())
                                    .flatMap(List::stream)
                                    .toList()
                    );
            schedulePersistenceService.saveCandidates(
                    tripId,
                    plannedRoutes,
                    placeIds,
                    preparation.mode()
            );
        } catch (RouteCalculationException exception) {
            ErrorCode errorCode = exception.getReason()
                    == RouteCalculationException.Reason.ROUTE_NOT_FOUND
                    ? ErrorCode.SCHEDULE_ROUTE_NOT_FOUND
                    : ErrorCode.INVALID_AI_PLACE_RESULT;
            throw new BusinessException(
                    errorCode,
                    exception
            );
        }

        return new SchedulePlaceSelectionResponse(
                tripId.toString(),
                response.data().places()
        );
    }

    private List<PlannedRoute> planRoutes(List<RecommendedRoute> routes) {
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

    private boolean isValidResponse(
            AiPlaceSelectionResponse response,
            long tripDayCount
    ) {
        if (response == null
                || response.statusCode() != 200
                || response.data() == null
                || response.data().places() == null) {
            return false;
        }

        return hasValidDailyPlaces(response.data().places(), tripDayCount)
                && isValidRoutes(response.data().routes(), tripDayCount);
    }

    private boolean isValidRoutes(
            List<AiPlaceSelectionResponse.Route> routes,
            long tripDayCount
    ) {
        if (routes == null || routes.size() != 2) {
            return false;
        }

        Set<AiPlaceSelectionResponse.Strategy> strategies = new HashSet<>();
        for (AiPlaceSelectionResponse.Route route : routes) {
            if (route == null
                    || route.strategy() == null
                    || !strategies.add(route.strategy())
                    || hasInvalidCounts(route)
                    || route.days() == null
                    || !hasValidDailyPlaces(route.days(), tripDayCount)) {
                return false;
            }
        }

        return strategies.equals(Set.of(
                AiPlaceSelectionResponse.Strategy.PREFERENCE,
                AiPlaceSelectionResponse.Strategy.DIVERSITY
        ));
    }

    private boolean hasInvalidCounts(AiPlaceSelectionResponse.Route route) {
        return route.preferenceCount() == null
                || route.preferenceCount() < 0
                || route.discoveryCount() == null
                || route.discoveryCount() < 0
                || route.preferenceCount() + route.discoveryCount() == 0;
    }

    private boolean hasValidDailyPlaces(
            List<List<AiPlaceSelectionResponse.Place>> dailyPlaces,
            long tripDayCount
    ) {
        if (dailyPlaces == null || dailyPlaces.size() != tripDayCount) {
            return false;
        }

        for (int dayIndex = 0; dayIndex < dailyPlaces.size(); dayIndex++) {
            int minimumCount = ScheduleDayPlacePolicy.minimumGeneratedPlaces(
                    dayIndex + 1,
                    dailyPlaces.size()
            );
            if (!isValidDailyPlaces(dailyPlaces.get(dayIndex), minimumCount)) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidDailyPlaces(
            List<AiPlaceSelectionResponse.Place> places,
            int minimumCount
    ) {
        if (places == null || places.size() < minimumCount) {
            return false;
        }

        Set<String> placeIds = new HashSet<>();
        return places.stream().allMatch(place ->
                isValidPlace(place) && placeIds.add(place.id())
        );
    }

    private boolean isValidPlace(AiPlaceSelectionResponse.Place place) {
        return place != null
                && place.id() != null
                && !place.id().isBlank()
                && place.displayName() != null
                && place.displayName().text() != null
                && !place.displayName().text().isBlank()
                && place.location() != null
                && Double.isFinite(place.location().latitude())
                && place.location().latitude() >= -90
                && place.location().latitude() <= 90
                && Double.isFinite(place.location().longitude())
                && place.location().longitude() >= -180
                && place.location().longitude() <= 180
                && place.types() != null
                && place.selectedFor() != null
                && place.matchedPreferences() != null;
    }

}
