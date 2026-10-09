package com.planit.schedule.service;

import com.planit.domain.Trip;
import com.planit.global.error.BusinessException;
import com.planit.repository.TripRepository;
import com.planit.schedule.ai.RecommendedPlace;
import com.planit.schedule.domain.Place;
import com.planit.schedule.domain.PlaceDetails;
import com.planit.schedule.domain.Schedule;
import com.planit.schedule.domain.ScheduleDay;
import com.planit.schedule.domain.ScheduleLeg;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.planit.global.error.ErrorCode.INVALID_AI_PLACE_RESULT;
import static com.planit.global.error.ErrorCode.SCHEDULE_ALREADY_EXISTS;
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
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .filter(value -> value.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(TRIP_NOT_FOUND));
        if (scheduleRepository.existsByTripIdAndActiveConfirmedSlot(
                tripId,
                (byte) 1
        )) {
            throw new BusinessException(SCHEDULE_ALREADY_EXISTS);
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
            LocalDateTime now
    ) {
        Map<Long, RecommendedPlace> recommendationByPlaceId = new HashMap<>();
        List<RoutePlace> routePlaces = new ArrayList<>();
        for (RecommendedPlace recommendation : recommendations) {
            Place place = upsertPlace(trip, recommendation, now);
            recommendationByPlaceId.put(place.getId(), recommendation);
            routePlaces.add(new RoutePlace(
                    place.getId(),
                    recommendation.latitude(),
                    recommendation.longitude(),
                    recommendation.categoryGroup()
            ));
        }

        RoutePlan plan = calculator.calculate(routePlaces);
        ScheduleDay day = dayRepository.save(new ScheduleDay(
                schedule,
                dayNumber,
                trip.getStartDate().plusDays(dayNumber - 1L)
        ));
        Map<Long, ScheduleVisit> visitByPlaceId = saveVisits(
                day,
                plan,
                recommendationByPlaceId,
                now
        );
        saveLegs(day, plan, visitByPlaceId);

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

    private Map<Long, ScheduleVisit> saveVisits(
            ScheduleDay day,
            RoutePlan plan,
            Map<Long, RecommendedPlace> recommendationByPlaceId,
            LocalDateTime now
    ) {
        Map<Long, ScheduleVisit> visitByPlaceId = new HashMap<>();
        for (int index = 0; index < plan.places().size(); index++) {
            RoutePlace routePlace = plan.places().get(index);
            Place place = placeRepository.getReferenceById(
                    routePlace.placeId()
            );
            RecommendedPlace recommendation = recommendationByPlaceId.get(
                    routePlace.placeId()
            );
            ScheduleVisit visit = visitRepository.save(new ScheduleVisit(
                    day,
                    place,
                    index + 1,
                    recommendation.editorialSummary(),
                    now
            ));
            visitByPlaceId.put(routePlace.placeId(), visit);
        }
        return visitByPlaceId;
    }

    private void saveLegs(
            ScheduleDay day,
            RoutePlan plan,
            Map<Long, ScheduleVisit> visitByPlaceId
    ) {
        for (RouteLeg leg : plan.legs()) {
            legRepository.save(new ScheduleLeg(
                    day,
                    visitByPlaceId.get(leg.fromPlaceId()),
                    visitByPlaceId.get(leg.toPlaceId()),
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
