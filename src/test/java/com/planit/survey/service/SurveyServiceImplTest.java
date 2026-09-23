package com.planit.survey.service;

import com.planit.domain.PreferenceQuestion;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.repository.PreferenceQuestionRepository;
import com.planit.repository.SurveyAnswerRepository;
import com.planit.repository.SurveyRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.survey.dto.SurveyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SurveyServiceImplTest {

    private static final String USER_PUBLIC_ID =
            "01991f6e-7300-7b21-a3cc-1436db3df95e";

    private UserRepository userRepository;
    private TripRepository tripRepository;
    private TripMemberRepository tripMemberRepository;
    private PreferenceQuestionRepository preferenceQuestionRepository;
    private SurveyRepository surveyRepository;
    private SurveyServiceImpl surveyService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tripRepository = mock(TripRepository.class);
        tripMemberRepository = mock(TripMemberRepository.class);
        preferenceQuestionRepository = mock(
                PreferenceQuestionRepository.class
        );
        surveyRepository = mock(SurveyRepository.class);
        SurveyAnswerRepository surveyAnswerRepository = mock(
                SurveyAnswerRepository.class
        );

        surveyService = new SurveyServiceImpl(
                userRepository,
                tripRepository,
                tripMemberRepository,
                preferenceQuestionRepository,
                surveyRepository,
                surveyAnswerRepository
        );
    }

    @Test
    void returnsNeutralScoresForDraftSurvey() {
        UUID publicId = UUID.fromString(USER_PUBLIC_ID);
        User user = mock(User.class);
        Trip trip = mock(Trip.class);
        TripMember member = mock(TripMember.class);
        PreferenceQuestion firstQuestion = questionWithId(10L);
        PreferenceQuestion secondQuestion = questionWithId(11L);

        when(userRepository.findByPublicIdAndDeletedAtIsNull(publicId))
                .thenReturn(Optional.of(user));
        when(tripRepository.findById(1001L)).thenReturn(Optional.of(trip));
        when(trip.getDeletedAt()).thenReturn(null);
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.of(member));
        when(preferenceQuestionRepository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(firstQuestion, secondQuestion));
        when(surveyRepository.findByTripMember(member))
                .thenReturn(Optional.empty());

        SurveyResponse response = surveyService.getMySurvey(
                USER_PUBLIC_ID,
                1001L
        );

        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.answers())
                .extracting(answer -> answer.questionId() + ":" + answer.score())
                .containsExactly("10:3", "11:3");
    }

    private PreferenceQuestion questionWithId(Long id) {
        PreferenceQuestion question = mock(PreferenceQuestion.class);
        when(question.getId()).thenReturn(id);
        return question;
    }
}
