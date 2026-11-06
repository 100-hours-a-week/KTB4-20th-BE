package com.planit.schedule.service;

import com.planit.domain.Trip;
import com.planit.global.error.BusinessException;
import com.planit.repository.TripRepository;
import com.planit.schedule.ai.RecommendedPlace;
import com.planit.schedule.ai.RecommendedRoute;
import com.planit.schedule.domain.Place;
import com.planit.schedule.domain.PlaceDetails;
import com.planit.schedule.domain.Schedule;
import com.planit.schedule.domain.ScheduleDay;
import com.planit.schedule.domain.ScheduleDayPlacePolicy;
import com.planit.schedule.domain.ScheduleLeg;
import com.planit.schedule.domain.ScheduleStrategy;
import com.planit.schedule.domain.ScheduleVisit;
import com.planit.schedule.repository.PlaceRepository;
import com.planit.schedule.repository.ScheduleDayRepository;
import com.planit.schedule.repository.ScheduleLegRepository;
import com.planit.schedule.repository.ScheduleRepository;
import com.planit.schedule.repository.ScheduleVisitRepository;
import com.planit.schedule.route.RouteCalculationException;
import com.planit.schedule.route.RouteLeg;
import com.planit.schedule.route.RoutePlace;
import com.planit.schedule.route.RoutePlan;
import com.planit.schedule.route.ShortestRouteCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.planit.global.error.ErrorCode.INVALID_AI_PLACE_RESULT;
import static com.planit.global.error.ErrorCode.SCHEDULE_ALREADY_EXISTS;
import static com.planit.global.error.ErrorCode.SCHEDULE_CANDIDATES_PENDING;
import static com.planit.global.error.ErrorCode.SCHEDULE_REGENERATION_LIMIT_EXCEEDED;
import static com.planit.global.error.ErrorCode.SCHEDULE_ROUTE_NOT_FOUND;
import static com.planit.global.error.ErrorCode.TRIP_NOT_FOUND;

@Service
public class SchedulePersistenceService {
    private final TripRepository tripRepository;
    private final PlaceRepository placeRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleDayRepository dayRepository;
    private final ScheduleVisitRepository visitRepository;
    private final ScheduleLegRepository legRepository;
    private final ShortestRouteCalculator calculator;

    public SchedulePersistenceService(
            TripRepository tripRepository,
            PlaceRepository placeRepository,
            ScheduleRepository scheduleRepository,
            ScheduleDayRepository dayRepository,
            ScheduleVisitRepository visitRepository,
            ScheduleLegRepository legRepository
    ) {
        this.tripRepository = tripRepository;
        this.placeRepository = placeRepository;
        this.scheduleRepository = scheduleRepository;
        this.dayRepository = dayRepository;
        this.visitRepository = visitRepository;
        this.legRepository = legRepository;
        this.calculator = new ShortestRouteCalculator();
    }

    @Transactional
    public GeneratedScheduleResult save(
            long tripId,
            List<List<RecommendedPlace>> dailyRecommendations
    ) {
        return save(tripId, dailyRecommendations, ScheduleGenerationMode.INITIAL);
    }

    @Transactional
    public List<GeneratedScheduleResult> saveCandidates(
            long tripId,
            List<PlannedRoute> plannedRoutes,
            Map<String, Long> placeIdsByGooglePlaceId,
            ScheduleGenerationMode mode
    ) {
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .filter(value -> value.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(TRIP_NOT_FOUND));
        validateCandidateValues(trip, plannedRoutes);
        if (scheduleRepository.existsByTripIdAndActiveConfirmedSlot(
                tripId,
                (byte) 1
        )) {
            replaceExistingSchedule(trip, mode);
        } else if (scheduleRepository.existsByTripIdAndStatus(tripId, "DRAFT")) {
            // 생성 요청은 AI 호출 전에 판정하므로, 동시에 들어온 요청은 여행 행 잠금 아래에서 다시 막는다.
            throw new BusinessException(SCHEDULE_CANDIDATES_PENDING);
        }

        LocalDateTime now = LocalDateTime.now();
        try {
            List<GeneratedScheduleResult> results = new ArrayList<>(3);
            for (PlannedRoute plannedRoute : plannedRoutes) {
                results.add(saveCandidate(
                        trip,
                        plannedRoute,
                        placeIdsByGooglePlaceId,
                        now
                ));
            }
            return List.copyOf(results);
        } catch (NullPointerException exception) {
            throw new BusinessException(INVALID_AI_PLACE_RESULT, exception);
        }
    }

    private void validateCandidateValues(
            Trip trip,
            List<PlannedRoute> plannedRoutes
    ) {
        if (plannedRoutes == null || plannedRoutes.size() != 3) {
            throw new BusinessException(INVALID_AI_PLACE_RESULT);
        }

        long tripDayCount = ChronoUnit.DAYS.between(
                trip.getStartDate(),
                trip.getEndDate()
        ) + 1;
        Set<ScheduleStrategy> strategies = new HashSet<>();
        for (PlannedRoute plannedRoute : plannedRoutes) {
            if (plannedRoute == null
                    || plannedRoute.dayPlans().size() != tripDayCount) {
                throw new BusinessException(INVALID_AI_PLACE_RESULT);
            }
            validateCandidateValue(
                    plannedRoute.route(),
                    tripDayCount,
                    strategies
            );
        }
        if (!strategies.equals(EnumSet.allOf(ScheduleStrategy.class))) {
            throw new BusinessException(INVALID_AI_PLACE_RESULT);
        }
    }

    private void validateCandidateValue(
            RecommendedRoute route,
            long tripDayCount,
            Set<ScheduleStrategy> strategies
    ) {
        if (route == null
                || route.strategy() == null
                || !strategies.add(route.strategy())
                || hasInvalidCounts(route)
                || route.days() == null
                || route.days().size() != tripDayCount
                || hasInvalidDailyPlaceCounts(route.days())) {
            throw new BusinessException(INVALID_AI_PLACE_RESULT);
        }
    }

    private boolean hasInvalidDailyPlaceCounts(
            List<List<RecommendedPlace>> days
    ) {
        for (int dayIndex = 0; dayIndex < days.size(); dayIndex++) {
            List<RecommendedPlace> places = days.get(dayIndex);
            int minimumCount = ScheduleDayPlacePolicy.minimumGeneratedPlaces(
                    dayIndex + 1,
                    days.size()
            );
            if (places == null || places.size() < minimumCount) {
                return true;
            }
        }
        return false;
    }

    private boolean hasInvalidCounts(RecommendedRoute route) {
        return route.strategy() == ScheduleStrategy.SHORTEST
                ? route.preferenceCount() != null
                || route.discoveryCount() != null
                : route.preferenceCount() == null
                || route.preferenceCount() < 0
                || route.discoveryCount() == null
                || route.discoveryCount() < 0
                || route.preferenceCount() + route.discoveryCount() == 0;
    }

    private GeneratedScheduleResult saveCandidate(
            Trip trip,
            PlannedRoute plannedRoute,
            Map<String, Long> placeIdsByGooglePlaceId,
            LocalDateTime now
    ) {
        RecommendedRoute route = plannedRoute.route();
        Schedule schedule = scheduleRepository.save(new Schedule(
                trip,
                route.strategy(),
                route.preferenceCount(),
                route.discoveryCount(),
                now
        ));
        List<String> dayIds = new ArrayList<>();
        long totalDistanceMeters = 0;
        int placeCount = 0;
        int legCount = 0;
        for (int dayIndex = 0; dayIndex < route.days().size(); dayIndex++) {
            SavedDayResult dayResult = savePlannedDay(
                    trip,
                    schedule,
                    (byte) (dayIndex + 1),
                    route.days().get(dayIndex),
                    plannedRoute.dayPlans().get(dayIndex),
                    placeIdsByGooglePlaceId,
                    now
            );
            dayIds.add(dayResult.dayId());
            totalDistanceMeters += dayResult.totalDistanceMeters();
            placeCount += dayResult.placeCount();
            legCount += dayResult.legCount();
        }
        return new GeneratedScheduleResult(
                schedule.getId().toString(),
                dayIds,
                totalDistanceMeters,
                placeCount,
                legCount
        );
    }

    /**
     * 생성된 일정을 저장한다. 재생성이면 여행 행을 잠근 상태에서 기존 일정을 삭제한 뒤 새 일정으로 대체하고,
     * 일반 재생성일 때만 재생성 횟수를 차감한다. 이후 단계가 실패하면 트랜잭션이 롤백되어 기존 일정과 횟수가 유지된다.
     */
    @Transactional
    public GeneratedScheduleResult save(
            long tripId,
            List<List<RecommendedPlace>> dailyRecommendations,
            ScheduleGenerationMode mode
    ) {
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .filter(value -> value.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(TRIP_NOT_FOUND));
        if (scheduleRepository.existsByTripIdAndActiveConfirmedSlot(
                tripId,
                (byte) 1
        )) {
            replaceExistingSchedule(trip, mode);
        }

        LocalDateTime now = LocalDateTime.now();
        try {
            long tripDayCount = ChronoUnit.DAYS.between(
                    trip.getStartDate(),
                    trip.getEndDate()
            ) + 1;
            if (dailyRecommendations == null
                    || dailyRecommendations.size() != tripDayCount) {
                throw new BusinessException(INVALID_AI_PLACE_RESULT);
            }

            Schedule schedule = scheduleRepository.save(new Schedule(trip, now));
            List<String> dayIds = new ArrayList<>();
            long totalDistanceMeters = 0;
            int placeCount = 0;
            int legCount = 0;

            for (int dayIndex = 0;
                    dayIndex < dailyRecommendations.size();
                    dayIndex++) {
                SavedDayResult dayResult = saveDay(
                        trip,
                        schedule,
                        (byte) (dayIndex + 1),
                        dailyRecommendations.get(dayIndex),
                        now,
                        false
                );
                dayIds.add(dayResult.dayId());
                totalDistanceMeters += dayResult.totalDistanceMeters();
                placeCount += dayResult.placeCount();
                legCount += dayResult.legCount();
            }

            return new GeneratedScheduleResult(
                    schedule.getId().toString(),
                    dayIds,
                    totalDistanceMeters,
                    placeCount,
                    legCount
            );
        } catch (RouteCalculationException exception) {
            if (exception.getReason()
                    == RouteCalculationException.Reason.ROUTE_NOT_FOUND) {
                throw new BusinessException(SCHEDULE_ROUTE_NOT_FOUND, exception);
            }
            throw new BusinessException(INVALID_AI_PLACE_RESULT, exception);
        } catch (NullPointerException exception) {
            throw new BusinessException(INVALID_AI_PLACE_RESULT, exception);
        }
    }

    private SavedDayResult saveDay(
            Trip trip,
            Schedule schedule,
            byte dayNumber,
            List<RecommendedPlace> recommendations,
            LocalDateTime now,
            boolean preserveOrder
    ) {
        return saveDay(
                trip,
                schedule,
                dayNumber,
                recommendations,
                now,
                null,
                preserveOrder
        );
    }

    private SavedDayResult savePlannedDay(
            Trip trip,
            Schedule schedule,
            byte dayNumber,
            List<RecommendedPlace> recommendations,
            RoutePlan plan,
            Map<String, Long> placeIdsByGooglePlaceId,
            LocalDateTime now
    ) {
        Map<String, RecommendedPlace> recommendationByGooglePlaceId =
                recommendations.stream().collect(Collectors.toMap(
                        RecommendedPlace::googlePlaceId,
                        recommendation -> recommendation
                ));
        ScheduleDay day = dayRepository.save(new ScheduleDay(
                schedule,
                dayNumber,
                trip.getStartDate().plusDays(dayNumber - 1L)
        ));
        Map<String, ScheduleVisit> visitByGooglePlaceId = new HashMap<>();
        for (int index = 0; index < plan.places().size(); index++) {
            String googlePlaceId = plan.places().get(index).googlePlaceId();
            Long placeId = placeIdsByGooglePlaceId.get(googlePlaceId);
            RecommendedPlace recommendation =
                    recommendationByGooglePlaceId.get(googlePlaceId);
            if (placeId == null || recommendation == null) {
                throw new BusinessException(INVALID_AI_PLACE_RESULT);
            }
            ScheduleVisit visit = visitRepository.save(new ScheduleVisit(
                    day,
                    placeRepository.getReferenceById(placeId),
                    index + 1,
                    recommendation.editorialSummary(),
                    recommendation.matchedPreferences(),
                    now
            ));
            visitByGooglePlaceId.put(googlePlaceId, visit);
        }
        saveLegs(day, plan, visitByGooglePlaceId);
        return new SavedDayResult(
                day.getId().toString(),
                plan.totalDistanceMeters(),
                plan.places().size(),
                plan.legs().size()
        );
    }

    private SavedDayResult saveDay(
            Trip trip,
            Schedule schedule,
            byte dayNumber,
            List<RecommendedPlace> recommendations,
            LocalDateTime now,
            RoutePlan plan
    ) {
        return saveDay(
                trip,
                schedule,
                dayNumber,
                recommendations,
                now,
                plan,
                false
        );
    }

    private SavedDayResult saveDay(
            Trip trip,
            Schedule schedule,
            byte dayNumber,
            List<RecommendedPlace> recommendations,
            LocalDateTime now,
            RoutePlan precomputedPlan,
            boolean preserveOrder
    ) {
        Map<String, RecommendedPlace> recommendationByGooglePlaceId =
                new HashMap<>();
        Map<String, Place> placeByGooglePlaceId = new HashMap<>();
        List<RoutePlace> routePlaces = new ArrayList<>();
        for (RecommendedPlace recommendation : recommendations) {
            Place place = upsertPlace(trip, recommendation, now);
            recommendationByGooglePlaceId.put(
                    recommendation.googlePlaceId(),
                    recommendation
            );
            placeByGooglePlaceId.put(recommendation.googlePlaceId(), place);
            routePlaces.add(new RoutePlace(
                    recommendation.googlePlaceId(),
                    recommendation.latitude(),
                    recommendation.longitude(),
                    recommendation.categoryGroup()
            ));
        }

        RoutePlan plan = precomputedPlan != null
                ? precomputedPlan
                : preserveOrder
                        ? calculator.calculateInGivenOrder(routePlaces)
                        : calculator.calculate(routePlaces);
        ScheduleDay day = dayRepository.save(new ScheduleDay(
                schedule,
                dayNumber,
                trip.getStartDate().plusDays(dayNumber - 1L)
        ));
        Map<String, ScheduleVisit> visitByGooglePlaceId = saveVisits(
                day,
                plan,
                recommendationByGooglePlaceId,
                placeByGooglePlaceId,
                now
        );
        saveLegs(day, plan, visitByGooglePlaceId);

        return new SavedDayResult(
                day.getId().toString(),
                plan.totalDistanceMeters(),
                plan.places().size(),
                plan.legs().size()
        );
    }

    private Place upsertPlace(
            Trip trip,
            RecommendedPlace recommendation,
            LocalDateTime now
    ) {
        PlaceDetails details = toPlaceDetails(recommendation);
        return placeRepository.findByRegion_IdAndGooglePlaceId(
                        trip.getRegion().getId(),
                        recommendation.googlePlaceId()
                )
                .map(existing -> {
                    existing.update(details, now);
                    return existing;
                })
                .orElseGet(() -> placeRepository.save(
                        new Place(trip.getRegion(), details, now)
                ));
    }

    private PlaceDetails toPlaceDetails(RecommendedPlace recommendation) {
        return new PlaceDetails(
                recommendation.googlePlaceId(),
                recommendation.name(),
                recommendation.categoryName(),
                null,
                null,
                BigDecimal.valueOf(recommendation.longitude()),
                BigDecimal.valueOf(recommendation.latitude()),
                null,
                null
        );
    }

    private Map<String, ScheduleVisit> saveVisits(
            ScheduleDay day,
            RoutePlan plan,
            Map<String, RecommendedPlace> recommendationByGooglePlaceId,
            Map<String, Place> placeByGooglePlaceId,
            LocalDateTime now
    ) {
        Map<String, ScheduleVisit> visitByGooglePlaceId = new HashMap<>();
        for (int index = 0; index < plan.places().size(); index++) {
            RoutePlace routePlace = plan.places().get(index);
            String googlePlaceId = routePlace.googlePlaceId();
            Place place = placeByGooglePlaceId.get(googlePlaceId);
            RecommendedPlace recommendation =
                    recommendationByGooglePlaceId.get(googlePlaceId);
            ScheduleVisit visit = visitRepository.save(new ScheduleVisit(
                    day,
                    place,
                    index + 1,
                    recommendation.editorialSummary(),
                    recommendation.matchedPreferences(),
                    now
            ));
            visitByGooglePlaceId.put(googlePlaceId, visit);
        }
        return visitByGooglePlaceId;
    }

    private void saveLegs(
            ScheduleDay day,
            RoutePlan plan,
            Map<String, ScheduleVisit> visitByGooglePlaceId
    ) {
        for (RouteLeg leg : plan.legs()) {
            legRepository.save(new ScheduleLeg(
                    day,
                    visitByGooglePlaceId.get(leg.fromGooglePlaceId()),
                    visitByGooglePlaceId.get(leg.toGooglePlaceId()),
                    leg.order(),
                    leg.distanceMeters()
            ));
        }
    }

    private record SavedDayResult(
            String dayId,
            long totalDistanceMeters,
            int placeCount,
            int legCount
    ) {
    }

    private void replaceExistingSchedule(Trip trip, ScheduleGenerationMode mode) {
        switch (mode) {
            case INITIAL -> throw new BusinessException(SCHEDULE_ALREADY_EXISTS);
            case GENERAL_REGENERATION -> {
                if (ScheduleRegenerationPolicy.remainingRegenerations(trip) == 0) {
                    throw new BusinessException(
                            SCHEDULE_REGENERATION_LIMIT_EXCEEDED
                    );
                }
                trip.increaseScheduleRegenerationCount();
            }
            case WEATHER_REGENERATION -> {
                // 날씨 사유 재생성은 재생성 횟수에 포함하지 않는다.
            }
        }
        deleteAllForTrip(trip.getId());
        // 확정 일정 UNIQUE(trip_id, active_confirmed_slot)를 위반하지 않도록 새 일정 저장 전에 삭제를 반영한다.
        scheduleRepository.flush();
    }

    /**
     * 여행방이 자동 삭제될 때(나가기·탈퇴로 마지막 한 명만 남는 경우) 후보·확정 일정과
     * 하위 Day·방문 장소·이동 구간을 함께 정리한다.
     */
    @Transactional
    public void deleteAllForTrip(Long tripId) {
        List<Schedule> schedules = scheduleRepository.findByTripId(tripId);
        for (Schedule schedule : schedules) {
            List<ScheduleDay> days = dayRepository
                    .findByScheduleIdOrderByDayNumberAsc(schedule.getId());
            for (ScheduleDay day : days) {
                legRepository.deleteAll(
                        legRepository.findByDayIdOrderByLegOrderAsc(day.getId())
                );
                visitRepository.deleteAll(
                        visitRepository.findByDayIdOrderByVisitOrderAsc(day.getId())
                );
            }
            dayRepository.deleteAll(days);
        }
        scheduleRepository.deleteAll(schedules);
    }
}
