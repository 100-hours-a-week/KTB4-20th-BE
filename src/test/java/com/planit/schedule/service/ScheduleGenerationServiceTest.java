package com.planit.schedule.service;

import com.planit.ai.AiPlaceSelectionRequest;
import com.planit.ai.AiPlaceSelectionResponse;
import com.planit.ai.AiTripClient;
import com.planit.domain.BroadRegion;
import com.planit.domain.SubRegion;
import com.planit.domain.Survey;
import com.planit.domain.SurveyAnswer;
import com.planit.domain.SurveyExcludedCategory;
import com.planit.domain.SurveyExclusionCategory;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleGenerationServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "01991f6e-7300-7b21-a3cc-1436db3df95e"
    );
    private static final long TRIP_ID = 100L;

    private UserRepository userRepository;
    private TripRepository tripRepository;
    private TripMemberRepository tripMemberRepository;
    private SurveyRepository surveyRepository;
    private SurveyAnswerRepository surveyAnswerRepository;
    private SurveyExcludedCategoryRepository excludedCategoryRepository;
    private AiTripClient aiTripClient;
    private ScheduleGenerationServiceImpl service;
    private User user;
    private Trip trip;
    private TripMember host;
    private Survey hostSurvey;

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
        aiTripClient = mock(AiTripClient.class);
        service = new ScheduleGenerationServiceImpl(
                userRepository,
                tripRepository,
                tripMemberRepository,
                surveyRepository,
                surveyAnswerRepository,
                excludedCategoryRepository,
                aiTripClient
        );

        user = mock(User.class);
        trip = mock(Trip.class);
        host = mock(TripMember.class);
        hostSurvey = mock(Survey.class);
        SubRegion region = mock(SubRegion.class);
        BroadRegion broadRegion = mock(BroadRegion.class);

        when(user.getPublicId()).thenReturn(USER_ID);
        when(userRepository.findByPublicIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(user));
        when(tripRepository.findById(TRIP_ID))
                .thenReturn(Optional.of(trip));
        when(trip.getId()).thenReturn(TRIP_ID);
        when(trip.getStartDate()).thenReturn(LocalDate.of(2026, 10, 1));
        when(trip.getSurveyDeadlineAt())
                .thenReturn(LocalDateTime.now().plusDays(1));
        when(trip.getSubRegion()).thenReturn(region);
        when(region.getBroadRegion()).thenReturn(broadRegion);
        when(broadRegion.getCode()).thenReturn("BR-SEOUL");
        when(host.getRole()).thenReturn(TripMemberRole.HOST);
        when(host.getActiveSlot()).thenReturn((byte) 1);
        when(host.getUser()).thenReturn(user);
        when(host.getTrip()).thenReturn(trip);
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.of(host));
        when(tripMemberRepository.findActiveMembersByTrip(trip))
                .thenReturn(List.of(host));
        when(hostSurvey.getTripMember()).thenReturn(host);
        when(hostSurvey.getSubmittedAt()).thenReturn(LocalDateTime.now());
        when(surveyRepository.findByTripMemberInAndSubmittedAtIsNotNull(
                List.of(host)
        )).thenReturn(List.of(hostSurvey));
        List<SurveyAnswer> submittedAnswers = answers();
        when(surveyAnswerRepository
                .findBySurveyOrderByPreferenceQuestionDisplayOrderAsc(
                        hostSurvey
                )).thenReturn(submittedAnswers);
        SurveyExcludedCategory seafood = excludedCategory("해산물");
        when(excludedCategoryRepository
                .findBySurveyOrderByExclusionCategoryIdAsc(hostSurvey))
                .thenReturn(List.of(seafood));
        when(aiTripClient.selectPlaces(any()))
                .thenReturn(aiResponse());
    }

    @Test
    void selectsPlacesForDayTrip() {
        SchedulePlaceSelectionResponse response = service.generate(
                USER_ID.toString(),
                TRIP_ID
        );

        ArgumentCaptor<AiPlaceSelectionRequest> requestCaptor =
                ArgumentCaptor.forClass(AiPlaceSelectionRequest.class);
        verify(aiTripClient).selectPlaces(requestCaptor.capture());
        AiPlaceSelectionRequest request = requestCaptor.getValue();

        assertThat(request.region()).isEqualTo("서울");
        assertThat(request.startDate())
                .isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(request.endDate()).isEqualTo(request.startDate());
        assertThat(request.members()).hasSize(1);
        assertThat(request.members().getFirst().surveyResult())
                .containsExactly(
                        1, 2, 3, 4, 5,
                        1, 2, 3, 4, 5,
                        1, 2, 3, 4, 5
                );
        assertThat(request.members().getFirst().dealBreakers())
                .containsExactly("해산물");
        assertThat(response.tripId()).isEqualTo("100");
        assertThat(response.places()).hasSize(1);
    }

    @Test
    void rejectsNonHost() {
        when(host.getRole()).thenReturn(TripMemberRole.MEMBER);

        assertError(ErrorCode.ACCESS_DENIED);

        verify(aiTripClient, never()).selectPlaces(any());
    }

    @Test
    void rejectsHostWithoutSubmittedSurvey() {
        when(surveyRepository.findByTripMemberInAndSubmittedAtIsNotNull(
                List.of(host)
        )).thenReturn(List.of());

        assertError(ErrorCode.SCHEDULE_GENERATION_NOT_READY);

        verify(aiTripClient, never()).selectPlaces(any());
    }

    @Test
    void rejectsSurveyWithoutFifteenAnswers() {
        List<SurveyAnswer> fourteenAnswers = answers().subList(0, 14);
        when(surveyAnswerRepository
                .findBySurveyOrderByPreferenceQuestionDisplayOrderAsc(
                        hostSurvey
                )).thenReturn(fourteenAnswers);

        assertError(ErrorCode.SCHEDULE_GENERATION_NOT_READY);

        verify(aiTripClient, never()).selectPlaces(any());
    }

    @Test
    void rejectsIncompleteSurveysBeforeDeadline() {
        TripMember member = mock(TripMember.class);
        when(tripMemberRepository.findActiveMembersByTrip(trip))
                .thenReturn(List.of(host, member));
        when(surveyRepository.findByTripMemberInAndSubmittedAtIsNotNull(
                List.of(host, member)
        )).thenReturn(List.of(hostSurvey));

        assertError(ErrorCode.SCHEDULE_GENERATION_NOT_READY);

        verify(aiTripClient, never()).selectPlaces(any());
    }

    @Test
    void selectsPlacesWithSubmittedMembersAfterDeadline() {
        TripMember member = mock(TripMember.class);
        when(trip.getSurveyDeadlineAt())
                .thenReturn(LocalDateTime.now().minusMinutes(1));
        when(tripMemberRepository.findActiveMembersByTrip(trip))
                .thenReturn(List.of(host, member));
        when(surveyRepository.findByTripMemberInAndSubmittedAtIsNotNull(
                List.of(host, member)
        )).thenReturn(List.of(hostSurvey));

        service.generate(USER_ID.toString(), TRIP_ID);

        verify(aiTripClient).selectPlaces(any());
    }

    @Test
    void rejectsUnsupportedRegion() {
        when(trip.getSubRegion().getBroadRegion().getCode())
                .thenReturn("BR-DAEGU");
        when(trip.getSubRegion().getName()).thenReturn("중구");

        assertError(ErrorCode.UNSUPPORTED_TRIP_REGION);

        verify(aiTripClient, never()).selectPlaces(any());
    }

    @Test
    void returnsFailureWhenAiCallFails() {
        when(aiTripClient.selectPlaces(any()))
                .thenThrow(new RestClientException("AI unavailable"));

        assertError(ErrorCode.AI_SCHEDULE_GENERATION_FAILED);
    }

    @Test
    void rejectsUnsuccessfulAiResponse() {
        when(aiTripClient.selectPlaces(any()))
                .thenReturn(new AiPlaceSelectionResponse(
                        500,
                        new AiPlaceSelectionResponse.Data(List.of())
                ));

        assertError(ErrorCode.AI_SCHEDULE_GENERATION_FAILED);
    }

    @Test
    void rejectsAiResponseWithoutPlaces() {
        when(aiTripClient.selectPlaces(any()))
                .thenReturn(new AiPlaceSelectionResponse(
                        200,
                        new AiPlaceSelectionResponse.Data(List.of())
                ));

        assertError(ErrorCode.AI_SCHEDULE_GENERATION_FAILED);
    }

    @Test
    void rejectsAiPlaceWithoutRequiredData() {
        AiPlaceSelectionResponse.Place invalidPlace =
                new AiPlaceSelectionResponse.Place(
                        " ",
                        null,
                        null,
                        null,
                        0,
                        0,
                        null,
                        null,
                        null
                );
        when(aiTripClient.selectPlaces(any()))
                .thenReturn(new AiPlaceSelectionResponse(
                        200,
                        new AiPlaceSelectionResponse.Data(
                                List.of(invalidPlace)
                        )
                ));

        assertError(ErrorCode.AI_SCHEDULE_GENERATION_FAILED);
    }

    private void assertError(ErrorCode expected) {
        assertThatThrownBy(() -> service.generate(
                USER_ID.toString(),
                TRIP_ID
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(expected)
        );
    }

    private List<SurveyAnswer> answers() {
        List<SurveyAnswer> answers = new ArrayList<>();
        for (int index = 0; index < 15; index++) {
            SurveyAnswer answer = mock(SurveyAnswer.class);
            when(answer.getScore()).thenReturn(index % 5 + 1);
            answers.add(answer);
        }
        return answers;
    }

    private SurveyExcludedCategory excludedCategory(String name) {
        SurveyExcludedCategory excluded = mock(SurveyExcludedCategory.class);
        SurveyExclusionCategory category = mock(
                SurveyExclusionCategory.class
        );
        when(category.getName()).thenReturn(name);
        when(excluded.getExclusionCategory()).thenReturn(category);
        return excluded;
    }

    private AiPlaceSelectionResponse aiResponse() {
        return new AiPlaceSelectionResponse(
                200,
                new AiPlaceSelectionResponse.Data(List.of(
                        new AiPlaceSelectionResponse.Place(
                                "google-place-1",
                                new AiPlaceSelectionResponse.DisplayName(
                                        "경복궁",
                                        "ko"
                                ),
                                new AiPlaceSelectionResponse.Location(
                                        37.5796,
                                        126.9770
                                ),
                                List.of("historical_landmark", "museum"),
                                4.6,
                                4820,
                                null,
                                List.of("user_id_1", "user_id_3"),
                                List.of("HISTORY_CULTURE")
                        )
                ))
        );
    }
}
