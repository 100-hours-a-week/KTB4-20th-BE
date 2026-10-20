package com.planit.schedule.service;

import com.planit.ai.AiPlaceSelectionRequest;
import com.planit.ai.AiPlaceSelectionResponse;
import com.planit.ai.AiTripClient;
import com.planit.domain.Region;
import com.planit.domain.Survey;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.repository.SurveyAnswerRepository;
import com.planit.repository.SurveyExcludedCategoryRepository;
import com.planit.repository.SurveyRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.schedule.domain.ScheduleGenerationReason;
import com.planit.schedule.dto.SchedulePlaceSelectionResponse;
import com.planit.schedule.ai.AiRecommendedPlaceMapper;
import com.planit.schedule.ai.RecommendedRoute;
import com.planit.schedule.route.RouteCalculationException;
import com.planit.trip.service.TripMemberAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScheduleGenerationServiceImpl
        implements ScheduleGenerationService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final int REQUIRED_PLACE_MIN_COUNT = 5;

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripMemberAccessService tripMemberAccessService;
    private final SurveyRepository surveyRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final SurveyExcludedCategoryRepository excludedCategoryRepository;
    private final AiTripClient aiTripClient;
    private final AiRecommendedPlaceMapper recommendedPlaceMapper;
    private final SchedulePersistenceService schedulePersistenceService;
    private final ScheduleRegenerationPolicy regenerationPolicy;

    @Override
    @Transactional
    public SchedulePlaceSelectionResponse generate(
            String userPublicId,
            Long tripId,
            ScheduleGenerationReason reason
    ) {
        User user = findActiveUser(userPublicId);
        Trip trip = findActiveTrip(tripId);
        TripMember host = tripMemberAccessService.findActiveHost(
                trip,
                user,
                ErrorCode.TRIP_HOST_REQUIRED
        );

        List<TripMember> activeMembers =
                tripMemberRepository.findActiveMembersByTrip(trip);
        List<Survey> submittedSurveys = surveyRepository
                .findByTripMemberInAndSubmittedAtIsNotNull(activeMembers);
        validateSurveyState(trip, host, activeMembers, submittedSurveys);
        ScheduleGenerationMode mode = regenerationPolicy.resolve(trip, reason);

        AiPlaceSelectionRequest request = new AiPlaceSelectionRequest(
                resolveAiRegion(trip.getRegion()),
                trip.getStartDate(),
                trip.getEndDate(),
                submittedSurveys.stream()
                        .map(this::toMemberSurvey)
                        .toList()
        );

        AiPlaceSelectionResponse response;
        try {
            response = aiTripClient.selectPlaces(request);
        } catch (RestClientException exception) {
            throw new BusinessException(
                    ErrorCode.AI_SCHEDULE_GENERATION_FAILED,
                    exception
            );
        }

        if (!isValidResponse(response, trip)) {
            throw new BusinessException(
                    ErrorCode.AI_SCHEDULE_GENERATION_FAILED
            );
        }

        try {
            List<RecommendedRoute> routes = recommendedPlaceMapper.mapRoutes(
                    response
            );
            schedulePersistenceService.saveCandidates(
                    tripId,
                    routes,
                    mode
            );
        } catch (RouteCalculationException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_AI_PLACE_RESULT,
                    exception
            );
        }

        return new SchedulePlaceSelectionResponse(
                trip.getId().toString(),
                response.data().places()
        );
    }

    private AiPlaceSelectionRequest.MemberSurvey toMemberSurvey(
            Survey survey
    ) {
        List<Integer> scores = surveyAnswerRepository
                .findBySurveyOrderByPreferenceQuestionDisplayOrderAsc(
                        survey
                )
                .stream()
                .map(answer -> answer.getScore())
                .toList();
        if (scores.size() != 15) {
            throw new BusinessException(
                    ErrorCode.SCHEDULE_GENERATION_NOT_READY
            );
        }

        return new AiPlaceSelectionRequest.MemberSurvey(
                new AiPlaceSelectionRequest.User(
                        survey.getTripMember()
                                .getUser()
                                .getPublicId()
                                .toString()
                ),
                scores,
                excludedCategoryRepository
                        .findBySurveyOrderByExclusionCategoryIdAsc(survey)
                        .stream()
                        .map(excluded -> excluded
                                .getExclusionCategory()
                                .getName())
                        .toList()
        );
    }

    private boolean isValidResponse(
            AiPlaceSelectionResponse response,
            Trip trip
    ) {
        if (response == null
                || response.statusCode() != 200
                || response.data() == null
                || response.data().places() == null) {
            return false;
        }

        long tripDayCount = ChronoUnit.DAYS.between(
                trip.getStartDate(),
                trip.getEndDate()
        ) + 1;
        return response.data().places().size() == tripDayCount
                && response.data().places().stream()
                .allMatch(this::isValidDailyPlaces)
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
                    || route.preferenceScore() == null
                    || route.days() == null
                    || route.days().size() != tripDayCount
                    || !route.days().stream()
                    .allMatch(this::isValidDailyPlaces)) {
                return false;
            }
        }

        return strategies.equals(Set.of(
                AiPlaceSelectionResponse.Strategy.PREFERENCE,
                AiPlaceSelectionResponse.Strategy.BALANCED
        ));
    }

    private boolean isValidDailyPlaces(
            List<AiPlaceSelectionResponse.Place> places
    ) {
        if (places == null || places.size() < REQUIRED_PLACE_MIN_COUNT) {
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

    private void validateSurveyState(
            Trip trip,
            TripMember host,
            List<TripMember> activeMembers,
            List<Survey> submittedSurveys
    ) {
        boolean hostSubmitted = submittedSurveys.stream()
                .anyMatch(survey -> survey.getTripMember().getUser()
                        .getPublicId()
                        .equals(host.getUser().getPublicId()));
        if (!hostSubmitted) {
            throw new BusinessException(
                    ErrorCode.SCHEDULE_GENERATION_NOT_READY
            );
        }

        boolean deadlinePassed = !LocalDateTime.now(SEOUL_ZONE)
                .isBefore(trip.getSurveyDeadlineAt());
        if (!deadlinePassed
                && submittedSurveys.size() != activeMembers.size()) {
            throw new BusinessException(
                    ErrorCode.SCHEDULE_GENERATION_NOT_READY
            );
        }
    }

    private String resolveAiRegion(Region region) {
        return switch (region.getCode()) {
            case "REGION-SEOUL" -> "서울";
            case "REGION-GYEONGJU" -> "경주";
            case "REGION-BUSAN" -> "부산";
            case "REGION-JEONJU" -> "전주";
            case "REGION-JEJU" -> "제주";
            default -> throw new BusinessException(
                    ErrorCode.UNSUPPORTED_TRIP_REGION
            );
        };
    }

    private Trip findActiveTrip(Long tripId) {
        return tripRepository.findById(tripId)
                .filter(trip -> trip.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_NOT_FOUND
                ));
    }

    private User findActiveUser(String userPublicId) {
        try {
            return userRepository.findByPublicIdAndDeletedAtIsNull(
                            UUID.fromString(userPublicId)
                    )
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.AUTHENTICATION_REQUIRED
                    ));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.AUTHENTICATION_REQUIRED,
                    exception
            );
        }
    }
}
