package com.planit.survey.service;

import com.planit.domain.PreferenceQuestion;
import com.planit.domain.Region;
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
import com.planit.survey.dto.SurveySummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SurveySummaryServiceTest {

    private static final long TRIP_ID = 1001L;
    private static final UUID HOST_PUBLIC_ID = UUID.fromString(
            "01991f6e-7300-7b21-a3cc-1436db3df95e"
    );
    private static final UUID MEMBER_PUBLIC_ID = UUID.fromString(
            "01991f6e-7300-7b21-a3cc-1436db3df95f"
    );
    private static final UUID WAITING_PUBLIC_ID = UUID.fromString(
            "01991f6e-7300-7b21-a3cc-1436db3df960"
    );

    private UserRepository userRepository;
    private TripRepository tripRepository;
    private TripMemberRepository tripMemberRepository;
    private SurveyRepository surveyRepository;
    private SurveyAnswerRepository surveyAnswerRepository;
    private SurveyExcludedCategoryRepository excludedCategoryRepository;
    private SurveyServiceImpl surveyService;
    private Trip trip;
    private TripMember host;
    private TripMember member;
    private TripMember waitingMember;
    private Survey hostSurvey;
    private Survey memberSurvey;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tripRepository = mock(TripRepository.class);
        tripMemberRepository = mock(TripMemberRepository.class);
        surveyRepository = mock(SurveyRepository.class);
        surveyAnswerRepository = mock(SurveyAnswerRepository.class);
        excludedCategoryRepository = mock(
                SurveyExcludedCategoryRepository.class
        );
        surveyService = new SurveyServiceImpl(
                userRepository,
                tripRepository,
                tripMemberRepository,
                mock(PreferenceQuestionRepository.class),
                surveyRepository,
                surveyAnswerRepository,
                mock(SurveyExclusionCategoryRepository.class),
                excludedCategoryRepository
        );
        givenTrip(LocalDateTime.of(2099, 9, 25, 23, 59));
    }

    @Test
    void summarizesSubmittedSurveysOfActiveMembers() {
        List<Survey> submittedSurveys = List.of(hostSurvey, memberSurvey);
        List<SurveyAnswer> answers = List.of(
                answer(hostSurvey, 5),
                answer(hostSurvey, 5),
                answer(hostSurvey, 5),
                answer(memberSurvey, 3),
                answer(memberSurvey, 3),
                answer(memberSurvey, 3)
        );
        SurveyExclusionCategory seafood = exclusionCategory();
        givenSubmittedSurveys(hostSurvey, memberSurvey);
        when(surveyAnswerRepository
                .findBySurveyInOrderByPreferenceQuestionDisplayOrderAsc(
                        submittedSurveys
                ))
                .thenReturn(answers);
        when(excludedCategoryRepository
                .findBySurveyInOrderByExclusionCategoryIdAsc(
                        submittedSurveys
                ))
                .thenReturn(List.of(
                        new SurveyExcludedCategory(hostSurvey, seafood),
                        new SurveyExcludedCategory(memberSurvey, seafood)
                ));

        SurveySummaryResponse response = getSummary();

        assertThat(response.activeMemberCount()).isEqualTo(3);
        assertThat(response.submittedCount()).isEqualTo(2);
        assertThat(response.progressPercent()).isEqualTo(67);
        assertThat(response.allSubmitted()).isFalse();
        assertThat(response.mySurveySubmitted()).isTrue();
        assertThat(response.memberSubmissions())
                .extracting(
                        SurveySummaryResponse.MemberSubmission::userPublicId,
                        SurveySummaryResponse.MemberSubmission::submitted
                )
                .containsExactly(
                        tuple(HOST_PUBLIC_ID, true),
                        tuple(MEMBER_PUBLIC_ID, true),
                        tuple(WAITING_PUBLIC_ID, false)
                );
        assertThat(response.categoryAverages())
                .containsExactly(new SurveySummaryResponse.CategoryAverage(
                        "FOOD",
                        4.0,
                        75
                ));
        assertThat(response.excludedCategories())
                .containsExactly(new SurveySummaryResponse.ExcludedCategory(
                        "SEAFOOD",
                        "해산물"
                ));
    }

    @Test
    void returnsEmptySummaryWhenNobodySubmitted() {
        givenTrip(LocalDateTime.of(2000, 1, 1, 0, 0));
        givenSubmittedSurveys();

        SurveySummaryResponse response = getSummary();

        assertThat(response.submittedCount()).isZero();
        assertThat(response.progressPercent()).isZero();
        assertThat(response.allSubmitted()).isFalse();
        assertThat(response.mySurveySubmitted()).isFalse();
        assertThat(response.deadlinePassed()).isTrue();
        assertThat(response.categoryAverages()).isEmpty();
        assertThat(response.excludedCategories()).isEmpty();
    }

    @Test
    void rejectsUserWhoIsNotActiveTripMember() {
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                host.getUser()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(this::getSummary)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TRIP_MEMBER_REQUIRED);

        verify(tripMemberRepository, never()).findActiveMembersByTrip(trip);
    }

    private void givenTrip(LocalDateTime deadlineAt) {
        trip = new Trip(
                mock(Region.class),
                "테스트 여행",
                LocalDate.of(2099, 9, 26),
                (byte) 4,
                deadlineAt
        );
        ReflectionTestUtils.setField(trip, "id", TRIP_ID);
        host = TripMember.createHost(
                trip,
                new User(null, HOST_PUBLIC_ID, "방장")
        );
        member = TripMember.createMember(
                trip,
                new User(null, MEMBER_PUBLIC_ID, "멤버")
        );
        waitingMember = TripMember.createMember(
                trip,
                new User(null, WAITING_PUBLIC_ID, "대기멤버")
        );
        hostSurvey = submittedSurvey(host);
        memberSurvey = submittedSurvey(member);

        when(userRepository.findByPublicIdAndDeletedAtIsNull(HOST_PUBLIC_ID))
                .thenReturn(Optional.of(host.getUser()));
        when(tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                host.getUser()
        )).thenReturn(Optional.of(host));
        when(tripMemberRepository.findActiveMembersByTrip(trip))
                .thenReturn(List.of(host, member, waitingMember));
    }

    private void givenSubmittedSurveys(Survey... surveys) {
        when(surveyRepository.findByTripMemberInAndSubmittedAtIsNotNull(
                List.of(host, member, waitingMember)
        )).thenReturn(List.of(surveys));
    }

    private SurveySummaryResponse getSummary() {
        return surveyService.getSurveySummary(
                HOST_PUBLIC_ID.toString(),
                TRIP_ID
        );
    }

    private Survey submittedSurvey(TripMember tripMember) {
        LocalDateTime submittedAt = LocalDateTime.of(2099, 9, 2, 0, 0);
        Survey survey = new Survey(tripMember, submittedAt.minusDays(1));
        survey.submit(submittedAt);
        return survey;
    }

    private SurveyAnswer answer(Survey survey, int score) {
        PreferenceQuestion question = mock(PreferenceQuestion.class);
        when(question.getCategoryCode()).thenReturn("FOOD");
        return new SurveyAnswer(survey, question, score);
    }

    private SurveyExclusionCategory exclusionCategory() {
        SurveyExclusionCategory category = mock(
                SurveyExclusionCategory.class
        );
        when(category.getCode()).thenReturn("SEAFOOD");
        when(category.getName()).thenReturn("해산물");
        return category;
    }
}
