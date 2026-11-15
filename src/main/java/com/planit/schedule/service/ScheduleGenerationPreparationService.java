package com.planit.schedule.service;

import com.planit.ai.AiPlaceSelectionRequest;
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
import com.planit.trip.service.TripMemberAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleGenerationPreparationService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final int PREFERENCE_QUESTION_COUNT = 15;

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripMemberAccessService tripMemberAccessService;
    private final SurveyRepository surveyRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final SurveyExcludedCategoryRepository excludedCategoryRepository;
    private final ScheduleRegenerationPolicy regenerationPolicy;

    @Transactional(readOnly = true)
    public ScheduleGenerationPreparation prepare(
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
                toMemberSurveys(submittedSurveys)
        );
        long tripDayCount = ChronoUnit.DAYS.between(
                trip.getStartDate(),
                trip.getEndDate()
        ) + 1;
        return new ScheduleGenerationPreparation(
                request,
                mode,
                trip.getRegion().getId(),
                tripDayCount,
                host.getId()
        );
    }

    private List<AiPlaceSelectionRequest.MemberSurvey> toMemberSurveys(
            List<Survey> submittedSurveys
    ) {
        Map<Long, List<Integer>> scoresBySurveyId = surveyAnswerRepository
                .findBySurveyInOrderByPreferenceQuestionDisplayOrderAsc(
                        submittedSurveys
                )
                .stream()
                .collect(Collectors.groupingBy(
                        answer -> answer.getSurvey().getId(),
                        Collectors.mapping(
                                answer -> answer.getScore(),
                                Collectors.toList()
                        )
                ));
        Map<Long, List<String>> exclusionsBySurveyId =
                excludedCategoryRepository
                        .findBySurveyInOrderByExclusionCategoryIdAsc(
                                submittedSurveys
                        )
                        .stream()
                        .collect(Collectors.groupingBy(
                                excluded -> excluded.getSurvey().getId(),
                                Collectors.mapping(
                                        excluded -> excluded
                                                .getExclusionCategory()
                                                .getName(),
                                        Collectors.toList()
                                )
                        ));

        return submittedSurveys.stream()
                .map(survey -> toMemberSurvey(
                        survey,
                        scoresBySurveyId.getOrDefault(
                                survey.getId(),
                                List.of()
                        ),
                        exclusionsBySurveyId.getOrDefault(
                                survey.getId(),
                                List.of()
                        )
                ))
                .toList();
    }

    private AiPlaceSelectionRequest.MemberSurvey toMemberSurvey(
            Survey survey,
            List<Integer> scores,
            List<String> exclusions
    ) {
        if (scores.size() != PREFERENCE_QUESTION_COUNT) {
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
                exclusions
        );
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
