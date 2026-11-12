package com.planit.schedule.ai;

import com.planit.ai.AiPlaceSelectionResponse;
import com.planit.schedule.domain.ScheduleDayPlacePolicy;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 장소 선택 응답이 일정 후보를 만들 수 있는 값인지 확인한다.
 * 동기 일정 생성과 메시지 큐 결과 반영이 같은 기준을 쓴다.
 */
@Component
public class ScheduleAiResultValidator {

    /**
     * 응답 코드가 200이고, 일차 수가 여행 기간과 같고, 일차별 최소 장소 수를 채우고,
     * PREFERENCE와 DIVERSITY 경로가 하나씩 있을 때 true다.
     */
    public boolean isValid(
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
