package com.planit.schedule.service;

import com.planit.ai.AiPlaceSelectionResponse;
import com.planit.ai.AiTripClient;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.schedule.domain.ScheduleGenerationReason;
import com.planit.schedule.dto.SchedulePlaceSelectionResponse;
import com.planit.schedule.ai.AiRecommendedPlaceMapper;
import com.planit.schedule.ai.RecommendedPlace;
import com.planit.schedule.ai.RecommendedRoute;
import com.planit.schedule.ai.ScheduleAiResultValidator;
import com.planit.schedule.route.RouteCalculationException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class ScheduleGenerationServiceImpl
        implements ScheduleGenerationService {

    private static final int PLACE_SAVE_MAX_ATTEMPTS = 3;

    private final ScheduleGenerationPreparationService preparationService;
    private final AiTripClient aiTripClient;
    private final AiRecommendedPlaceMapper recommendedPlaceMapper;
    private final PlacePersistenceService placePersistenceService;
    private final SchedulePersistenceService schedulePersistenceService;
    private final ScheduleAiResultValidator resultValidator;
    private final ScheduleRoutePlanner routePlanner;

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

        if (!resultValidator.isValid(response, preparation.tripDayCount())) {
            throw new BusinessException(
                    ErrorCode.AI_SCHEDULE_GENERATION_FAILED
            );
        }

        try {
            List<RecommendedRoute> routes = recommendedPlaceMapper.mapRoutes(
                    response
            );
            List<PlannedRoute> plannedRoutes = routePlanner.plan(routes);
            Map<String, Long> placeIds = savePlacesWithRetry(
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

    private Map<String, Long> savePlacesWithRetry(
            long regionId,
            List<RecommendedPlace> places
    ) {
        for (int attempt = 1; ; attempt++) {
            try {
                return placePersistenceService.upsertAndFindIds(
                        regionId,
                        places
                );
            } catch (PessimisticLockingFailureException exception) {
                if (attempt == PLACE_SAVE_MAX_ATTEMPTS) {
                    throw exception;
                }
                sleepBeforeRetry(attempt, exception);
            }
        }
    }

    private void sleepBeforeRetry(
            int attempt,
            PessimisticLockingFailureException exception
    ) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(
                    10L * attempt,
                    30L * attempt + 1
            ));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

}
