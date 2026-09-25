package com.planit.survey.service;

import com.planit.domain.PreferenceQuestion;
import com.planit.domain.Survey;
import com.planit.domain.SurveyAnswer;
import com.planit.domain.SurveyExcludedCategory;
import com.planit.domain.SurveyExclusionCategory;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.repository.PreferenceQuestionRepository;
import com.planit.repository.SurveyAnswerRepository;
import com.planit.repository.SurveyExcludedCategoryRepository;
import com.planit.repository.SurveyExclusionCategoryRepository;
import com.planit.repository.SurveyRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.survey.dto.SurveyAnswerRequest;
import com.planit.survey.dto.SurveyAnswerResponse;
import com.planit.survey.dto.SurveyResponse;
import com.planit.survey.dto.SurveySaveRequest;
import com.planit.survey.dto.SurveySummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyServiceImpl implements SurveyService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final PreferenceQuestionRepository preferenceQuestionRepository;
    private final SurveyRepository surveyRepository;
    private final SurveyAnswerRepository surveyAnswerRepository;
    private final SurveyExclusionCategoryRepository
            surveyExclusionCategoryRepository;
    private final SurveyExcludedCategoryRepository
            surveyExcludedCategoryRepository;

    @Override
    public SurveyResponse getMySurvey(String userPublicId, Long tripId) {
        TripMember member = findActiveMember(userPublicId, tripId);         //해당 여행방 멤버인지 확인
        List<PreferenceQuestion> questions = preferenceQuestionRepository
                .findAllByOrderByDisplayOrderAsc();

        return surveyRepository.findByTripMember(member)
                .filter(survey -> survey.getSubmittedAt() != null)
                .map(survey -> submittedResponse(tripId, survey))
                .orElseGet(() -> draftResponse(tripId, questions));
    }

    @Override
    public SurveySummaryResponse getSurveySummary(
            String userPublicId,
            Long tripId
    ) {
        TripMember currentMember = findActiveMember(userPublicId, tripId);
        Trip trip = currentMember.getTrip();
        List<TripMember> activeMembers =
                tripMemberRepository.findActiveMembersByTrip(trip);
        List<Survey> submittedSurveys = surveyRepository
                .findByTripMemberInAndSubmittedAtIsNotNull(activeMembers);

        Set<UUID> submittedUserIds = submittedSurveys.stream()
                .map(Survey::getTripMember)
                .map(TripMember::getUser)
                .map(User::getPublicId)
                .collect(Collectors.toSet());

        int activeMemberCount = activeMembers.size();
        int submittedCount = submittedSurveys.size();
        int progressPercent = activeMemberCount == 0
                ? 0
                : (int) Math.round(
                        submittedCount * 100.0 / activeMemberCount
                );
        LocalDateTime deadlineAt = trip.getSurveyDeadlineAt();

        return new SurveySummaryResponse(
                trip.getId().toString(),
                deadlineAt.atZone(SEOUL_ZONE).toOffsetDateTime(),
                activeMemberCount,
                submittedCount,
                progressPercent,
                activeMemberCount > 0
                        && submittedCount == activeMemberCount,
                submittedUserIds.contains(
                        currentMember.getUser().getPublicId()
                ),
                !LocalDateTime.now(SEOUL_ZONE).isBefore(deadlineAt),
                activeMembers.stream()
                        .map(member ->
                                new SurveySummaryResponse.MemberSubmission(
                                        member.getUser().getPublicId(),
                                        submittedUserIds.contains(
                                                member.getUser().getPublicId()
                                        )
                                )
                        )
                        .toList(),
                summarizePreferences(submittedSurveys),
                summarizeExcludedCategories(submittedSurveys)
        );
    }

    @Override
    @Transactional
    public SurveyResponse saveMySurvey(
            String userPublicId,
            Long tripId,
            SurveySaveRequest request
    ) {
        TripMember member = findActiveMember(userPublicId, tripId);
        List<PreferenceQuestion> questions = preferenceQuestionRepository
                .findAllByOrderByDisplayOrderAsc();
        Map<Long, PreferenceQuestion> questionsById = questions.stream()
                .collect(Collectors.toMap(
                        PreferenceQuestion::getId,
                        Function.identity()
                ));

        List<Long> requestedQuestionIds = parseQuestionIds(request.answers());
        validateCompleteAnswers(requestedQuestionIds, questionsById);

        List<Long> requestedExclusionCategoryIds = parseIds(
                request.excludedCategoryIds()
        );
        validateNoDuplicates(requestedExclusionCategoryIds);
        Map<Long, SurveyExclusionCategory> exclusionCategoriesById =
                findExclusionCategories(requestedExclusionCategoryIds);

        LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
        Survey survey = surveyRepository.findByTripMember(member)
                .orElseGet(() -> new Survey(member, now));
        validateSubmissionWindow(member.getTrip(), survey, now);

        Survey savedSurvey = surveyRepository.save(survey);
        surveyAnswerRepository.deleteBySurvey(savedSurvey);
        surveyExcludedCategoryRepository.deleteBySurvey(savedSurvey);
        surveyAnswerRepository.flush();
        surveyExcludedCategoryRepository.flush();

        List<SurveyAnswer> answers = request.answers().stream()
                .map(answer -> new SurveyAnswer(
                        savedSurvey,
                        questionsById.get(Long.parseLong(answer.questionId())),
                        answer.score()
                ))
                .toList();
        surveyAnswerRepository.saveAll(answers);

        List<SurveyExcludedCategory> excludedCategories =
                requestedExclusionCategoryIds.stream()
                        .map(categoryId -> new SurveyExcludedCategory(
                                savedSurvey,
                                exclusionCategoriesById.get(categoryId)
                        ))
                        .toList();
        surveyExcludedCategoryRepository.saveAll(excludedCategories);
        savedSurvey.submit(now);

        return submittedResponse(tripId, savedSurvey);
    }

    private TripMember findActiveMember(String userPublicId, Long tripId) {
        User user = findActiveUser(userPublicId);
        Trip trip = tripRepository.findById(tripId)
                .filter(found -> found.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_NOT_FOUND
                ));

        return tripMemberRepository
                .findByTripAndUserAndLeftAtIsNull(trip, user)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_MEMBER_REQUIRED
                ));
    }

    private User findActiveUser(String userPublicId) {
        try {
            return userRepository
                    .findByPublicIdAndDeletedAtIsNull(
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

    private List<Long> parseQuestionIds(List<SurveyAnswerRequest> answers) {
        try {
            return answers.stream()
                    .map(answer -> Long.parseLong(answer.questionId()))
                    .toList();
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
        }
    }

    private List<SurveySummaryResponse.CategoryAverage>
    summarizePreferences(List<Survey> surveys) {
        if (surveys.isEmpty()) {
            return List.of();
        }

        Map<String, Double> averageByCategory =
                surveyAnswerRepository
                        .findBySurveyInOrderByPreferenceQuestionDisplayOrderAsc(
                                surveys
                        )
                        .stream()
                        .collect(Collectors.groupingBy(
                                answer -> answer.getPreferenceQuestion()
                                        .getCategoryCode(),
                                LinkedHashMap::new,
                                Collectors.averagingDouble(
                                        SurveyAnswer::getScore
                                )
                        ));

        return averageByCategory.entrySet().stream()
                .map(entry -> categoryPreference(
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();
    }

    private SurveySummaryResponse.CategoryAverage categoryPreference(
            String categoryCode,
            double averageScore
    ) {
        double roundedAverage = Math.round(averageScore * 10.0) / 10.0;
        int preferencePercent = (int) Math.round(
                (averageScore - 1.0) / 4.0 * 100.0
        );

        return new SurveySummaryResponse.CategoryAverage(
                categoryCode,
                roundedAverage,
                preferencePercent
        );
    }

    private List<SurveySummaryResponse.ExcludedCategory>
    summarizeExcludedCategories(List<Survey> surveys) {
        if (surveys.isEmpty()) {
            return List.of();
        }

        Map<String, SurveySummaryResponse.ExcludedCategory> categoriesByCode =
                new LinkedHashMap<>();
        surveyExcludedCategoryRepository
                .findBySurveyInOrderByExclusionCategoryIdAsc(surveys)
                .forEach(excluded -> {
                    SurveyExclusionCategory category =
                            excluded.getExclusionCategory();
                    categoriesByCode.putIfAbsent(
                            category.getCode(),
                            new SurveySummaryResponse.ExcludedCategory(
                                    category.getCode(),
                                    category.getName()
                            )
                    );
                });

        return List.copyOf(categoriesByCode.values());
    }

    private List<Long> parseIds(List<String> ids) {
        try {
            return ids.stream()
                    .map(Long::parseLong)
                    .toList();
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, exception);
        }
    }

    private void validateNoDuplicates(List<Long> ids) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private Map<Long, SurveyExclusionCategory> findExclusionCategories(
            List<Long> categoryIds
    ) {
        Map<Long, SurveyExclusionCategory> categoriesById =
                surveyExclusionCategoryRepository.findAllById(categoryIds)
                        .stream()
                        .collect(Collectors.toMap(
                                SurveyExclusionCategory::getId,
                                Function.identity()
                        ));

        if (categoriesById.size() != categoryIds.size()) {
            throw new BusinessException(
                    ErrorCode.EXCLUSION_CATEGORY_NOT_FOUND
            );
        }

        return categoriesById;
    }

    private void validateCompleteAnswers(
            List<Long> requestedQuestionIds,
            Map<Long, PreferenceQuestion> questionsById
    ) {
        if (requestedQuestionIds.size() != questionsById.size()
                || new HashSet<>(requestedQuestionIds).size()
                != requestedQuestionIds.size()
                || !questionsById.keySet().containsAll(requestedQuestionIds)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private void validateSubmissionWindow(
            Trip trip,
            Survey survey,
            LocalDateTime now
    ) {
        if (survey.getSubmittedAt() == null
                && now.isAfter(trip.getSurveyDeadlineAt())) {
            throw new BusinessException(
                    ErrorCode.SURVEY_SUBMISSION_CLOSED
            );
        }

        LocalDate today = now.toLocalDate();
        if (survey.getSubmittedAt() != null
                && !today.isBefore(trip.getStartDate())) {
            throw new BusinessException(
                    ErrorCode.SURVEY_RESUBMISSION_CLOSED
            );
        }
    }

    private SurveyResponse draftResponse(
            Long tripId,
            List<PreferenceQuestion> questions
    ) {
        List<SurveyAnswerResponse> answers = questions.stream()
                .map(question -> new SurveyAnswerResponse(
                        question.getId().toString(),
                        3
                ))
                .toList();

        return new SurveyResponse(
                tripId.toString(),
                "DRAFT",
                null,
                answers,
                List.of()
        );
    }

    private SurveyResponse submittedResponse(Long tripId, Survey survey) {
        List<SurveyAnswerResponse> answers = surveyAnswerRepository
                .findBySurveyOrderByPreferenceQuestionDisplayOrderAsc(survey)
                .stream()
                .map(answer -> new SurveyAnswerResponse(
                        answer.getPreferenceQuestion().getId().toString(),
                        answer.getScore()
                ))
                .toList();

        List<String> excludedCategoryIds = surveyExcludedCategoryRepository
                .findBySurveyOrderByExclusionCategoryIdAsc(survey)
                .stream()
                .map(excludedCategory -> excludedCategory
                        .getExclusionCategory()
                        .getId()
                        .toString())
                .toList();

        return new SurveyResponse(
                tripId.toString(),
                "SUBMITTED",
                survey.getSubmittedAt()
                        .atZone(SEOUL_ZONE)
                        .toOffsetDateTime(),
                answers,
                excludedCategoryIds
        );
    }
}
