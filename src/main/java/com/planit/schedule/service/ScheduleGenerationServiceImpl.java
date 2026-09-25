package com.planit.schedule.service;

import com.planit.ai.AiPlaceSelectionRequest;
import com.planit.ai.AiPlaceSelectionResponse;
import com.planit.ai.AiTripClient;
import com.planit.domain.SubRegion;
import com.planit.domain.Survey;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.TripMemberRole;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.repository.SurveyAnswerRepository;
import com.planit.repository.SurveyExcludedCategoryRepository;
import com.planit.repository.SurveyRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.schedule.dto.SchedulePlaceSelectionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScheduleGenerationServiceImpl
        implements ScheduleGenerationService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final SurveyRepository surveyRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final SurveyExcludedCategoryRepository excludedCategoryRepository;
    private final AiTripClient aiTripClient;

    @Override
    @Transactional(readOnly = true)
    public SchedulePlaceSelectionResponse generate(
            String userPublicId,
            Long tripId
    ) {
        User user = findActiveUser(userPublicId);
        Trip trip = findActiveTrip(tripId);
        TripMember host = findActiveHost(trip, user);

        List<TripMember> activeMembers =
                tripMemberRepository.findActiveMembersByTrip(trip);
        List<Survey> submittedSurveys = surveyRepository
                .findByTripMemberInAndSubmittedAtIsNotNull(activeMembers);
        validateSurveyState(trip, host, activeMembers, submittedSurveys);

        AiPlaceSelectionRequest request = new AiPlaceSelectionRequest(
                resolveAiRegion(trip.getSubRegion()),
                trip.getStartDate(),
                trip.getStartDate(),
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

        if (!isValidResponse(response)) {
            throw new BusinessException(
                    ErrorCode.AI_SCHEDULE_GENERATION_FAILED
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

    private boolean isValidResponse(AiPlaceSelectionResponse response) {
        return response.statusCode() == 200
                && response.data() != null
                && response.data().places() != null
                && !response.data().places().isEmpty()
                && response.data().places().stream()
                .allMatch(this::isValidPlace);
    }

    private boolean isValidPlace(AiPlaceSelectionResponse.Place place) {
        return place.id() != null
                && !place.id().isBlank()
                && place.displayName() != null
                && place.displayName().text() != null
                && !place.displayName().text().isBlank()
                && place.location() != null
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

    private String resolveAiRegion(SubRegion region) {
        return switch (region.getBroadRegion().getCode()) {
            case "BR-SEOUL" -> "서울";
            case "BR-BUSAN" -> "부산";
            case "BR-JEJU" -> "제주";
            default -> switch (region.getName()) {
                case "경주시" -> "경주";
                case "전주시" -> "전주";
                default -> throw new BusinessException(
                        ErrorCode.UNSUPPORTED_TRIP_REGION
                );
            };
        };
    }

    private TripMember findActiveHost(Trip trip, User user) {
        TripMember member = tripMemberRepository
                .findByTripAndUserAndLeftAtIsNull(trip, user)
                .filter(found -> found.getActiveSlot() != null
                        && found.getActiveSlot() == 1)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_MEMBER_REQUIRED
                ));
        if (member.getRole() != TripMemberRole.HOST) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return member;
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
