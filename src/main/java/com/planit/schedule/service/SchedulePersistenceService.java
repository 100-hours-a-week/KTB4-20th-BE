package com.planit.schedule.service;

import com.planit.domain.Trip;
import com.planit.global.error.BusinessException;
import com.planit.repository.TripRepository;
import com.planit.schedule.ai.RecommendedPlace;
import com.planit.schedule.domain.Place;
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

import java.time.LocalDateTime;
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
            List<RecommendedPlace> recommendations
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
        Map<Long, RecommendedPlace> recommendationByPlaceId = new HashMap<>();
        List<RoutePlace> routePlaces = new ArrayList<>();
        try {
            for (RecommendedPlace recommendation : recommendations) {
                Place place = placeRepository
                        .findByRegion_IdAndGooglePlaceId(
                                trip.getSubRegion().getId(),
                                recommendation.googlePlaceId()
                        )
                        .map(existing -> {
                            existing.update(recommendation, now);
                            return existing;
                        })
                        .orElseGet(() -> placeRepository.save(
                                new Place(trip.getSubRegion(), recommendation, now)
                        ));
                recommendationByPlaceId.put(place.getId(), recommendation);
                routePlaces.add(new RoutePlace(
                        place.getId(),
                        recommendation.latitude(),
                        recommendation.longitude(),
                        recommendation.categoryGroup()
                ));
            }

            RoutePlan plan = calculator.calculate(routePlaces);
            Schedule schedule = scheduleRepository.save(new Schedule(trip, now));
            ScheduleDay day = dayRepository.save(
                    new ScheduleDay(schedule, trip.getStartDate())
            );
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
            for (RouteLeg leg : plan.legs()) {
                legRepository.save(new ScheduleLeg(
                        day,
                        visitByPlaceId.get(leg.fromPlaceId()),
                        visitByPlaceId.get(leg.toPlaceId()),
                        leg.order(),
                        leg.distanceMeters()
                ));
            }
            return new GeneratedScheduleResult(
                    schedule.getId().toString(),
                    day.getId().toString(),
                    plan.totalDistanceMeters(),
                    plan.places().size(),
                    plan.legs().size()
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
}
