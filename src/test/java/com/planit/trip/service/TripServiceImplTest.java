package com.planit.trip.service;

import com.planit.auth.token.SecureTokenGenerator;
import com.planit.auth.token.TokenHasher;
import com.planit.domain.SubRegion;
import com.planit.domain.Trip;
import com.planit.domain.TripInvitation;
import com.planit.domain.TripMember;
import com.planit.domain.TripMemberRole;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.repository.SubRegionRepository;
import com.planit.repository.TripInvitationRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.trip.dto.TripCreateRequest;
import com.planit.trip.dto.TripCreateResponse;
import com.planit.trip.dto.TripJoinRequest;
import com.planit.trip.dto.TripJoinResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripServiceImplTest {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final UUID USER_PUBLIC_ID = UUID.fromString(
            "01991f6e-7300-7b21-a3cc-1436db3df95e"
    );
    private static final String INVITATION_TOKEN = "a".repeat(43);
    private static final String INVITATION_TOKEN_HASH = "token-hash";

    private UserRepository userRepository;
    private SubRegionRepository subRegionRepository;
    private TripRepository tripRepository;
    private TripMemberRepository tripMemberRepository;
    private TripInvitationRepository tripInvitationRepository;
    private SecureTokenGenerator secureTokenGenerator;
    private TokenHasher tokenHasher;
    private TripServiceImpl tripService;
    private User user;
    private SubRegion subRegion;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        subRegionRepository = mock(SubRegionRepository.class);
        tripRepository = mock(TripRepository.class);
        tripMemberRepository = mock(TripMemberRepository.class);
        tripInvitationRepository = mock(TripInvitationRepository.class);
        secureTokenGenerator = mock(SecureTokenGenerator.class);
        tokenHasher = mock(TokenHasher.class);
        tripService = new TripServiceImpl(
                userRepository,
                subRegionRepository,
                tripRepository,
                tripMemberRepository,
                tripInvitationRepository,
                secureTokenGenerator,
                tokenHasher
        );

        user = mock(User.class);
        subRegion = mock(SubRegion.class);

        when(userRepository.findByPublicIdAndDeletedAtIsNull(USER_PUBLIC_ID))
                .thenReturn(Optional.of(user));
        when(subRegionRepository.findById(1L))
                .thenReturn(Optional.of(subRegion));
        when(tripRepository.save(any(Trip.class)))
                .thenAnswer(invocation -> {
                    Trip trip = invocation.getArgument(0);
                    ReflectionTestUtils.setField(trip, "id", 100L);
                    return trip;
                });
        when(tripMemberRepository.save(any(TripMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(secureTokenGenerator.generate())
                .thenReturn(INVITATION_TOKEN);
        when(tokenHasher.sha256(INVITATION_TOKEN))
                .thenReturn(INVITATION_TOKEN_HASH);
        when(tripInvitationRepository.save(any(TripInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsTripHostMembershipAndInvitation() {
        LocalDate today = LocalDate.now(SEOUL_ZONE);
        LocalDate startDate = today.plusDays(5);
        LocalDate deadlineDate = today.plusDays(1);

        TripCreateResponse response = tripService.createTrip(
                USER_PUBLIC_ID.toString(),
                request(startDate, deadlineDate)
        );

        ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(tripCaptor.capture());
        Trip savedTrip = tripCaptor.getValue();
        assertThat(response.tripId()).isEqualTo("100");
        assertThat(savedTrip.getStartDate()).isEqualTo(startDate);
        assertThat(savedTrip.getEndDate()).isEqualTo(startDate);
        assertThat(savedTrip.getSurveyDeadlineAt()).isEqualTo(
                deadlineDate.atTime(23, 59, 59, 999_999_000)
        );

        ArgumentCaptor<TripMember> memberCaptor =
                ArgumentCaptor.forClass(TripMember.class);
        verify(tripMemberRepository).save(memberCaptor.capture());
        TripMember host = memberCaptor.getValue();
        assertThat(host.getTrip()).isSameAs(savedTrip);
        assertThat(host.getUser()).isSameAs(user);
        assertThat(host.getRole()).isEqualTo(TripMemberRole.HOST);

        ArgumentCaptor<TripInvitation> invitationCaptor =
                ArgumentCaptor.forClass(TripInvitation.class);
        verify(tripInvitationRepository).save(invitationCaptor.capture());
        TripInvitation invitation = invitationCaptor.getValue();
        assertThat(invitation.getTrip()).isSameAs(savedTrip);
        assertThat(invitation.getTokenHash())
                .isEqualTo(INVITATION_TOKEN_HASH);
        assertThat(response.invitationToken())
                .isEqualTo(INVITATION_TOKEN);
    }

    @Test
    void findsTripUsingInvitationToken() {
        Trip invitedTrip = trip(200L);
        when(tripInvitationRepository.findByTokenHash(
                INVITATION_TOKEN_HASH
        )).thenReturn(Optional.of(new TripInvitation(
                invitedTrip,
                INVITATION_TOKEN_HASH
        )));

        TripJoinResponse response = tripService.joinTrip(
                USER_PUBLIC_ID.toString(),
                new TripJoinRequest(INVITATION_TOKEN)
        );

        assertThat(response.tripId()).isEqualTo("200");
        verify(tokenHasher).sha256(INVITATION_TOKEN);
        verify(tripInvitationRepository)
                .findByTokenHash(INVITATION_TOKEN_HASH);
    }

    @Test
    void rejectsMissingInvitationToken() {
        when(tripInvitationRepository.findByTokenHash(
                INVITATION_TOKEN_HASH
        )).thenReturn(Optional.empty());

        assertError(
                () -> tripService.joinTrip(
                        USER_PUBLIC_ID.toString(),
                        new TripJoinRequest(INVITATION_TOKEN)
                ),
                ErrorCode.RESOURCE_NOT_FOUND
        );
    }

    @Test
    void rejectsInvitationForDeletedTrip() {
        Trip deletedTrip = trip(200L);
        ReflectionTestUtils.setField(
                deletedTrip,
                "deletedAt",
                java.time.LocalDateTime.now()
        );
        when(tripInvitationRepository.findByTokenHash(
                INVITATION_TOKEN_HASH
        )).thenReturn(Optional.of(new TripInvitation(
                deletedTrip,
                INVITATION_TOKEN_HASH
        )));

        assertError(
                () -> tripService.joinTrip(
                        USER_PUBLIC_ID.toString(),
                        new TripJoinRequest(INVITATION_TOKEN)
                ),
                ErrorCode.RESOURCE_NOT_FOUND
        );
    }

    @Test
    void usesDayBeforeTripAsDefaultSurveyDeadline() {
        LocalDate startDate = LocalDate.now(SEOUL_ZONE).plusDays(5);

        tripService.createTrip(
                USER_PUBLIC_ID.toString(),
                request(startDate, null)
        );

        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());
        assertThat(captor.getValue().getSurveyDeadlineAt()).isEqualTo(
                startDate.minusDays(1)
                        .atTime(23, 59, 59, 999_999_000)
        );
    }

    @Test
    void fixesTodayTripSurveyDeadlineAtNoon() {
        LocalDate today = LocalDate.now(SEOUL_ZONE);

        tripService.createTrip(
                USER_PUBLIC_ID.toString(),
                request(today, null)
        );

        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());
        assertThat(captor.getValue().getSurveyDeadlineAt())
                .isEqualTo(today.atTime(12, 0));
    }

    @Test
    void rejectsPastTripDate() {
        LocalDate yesterday = LocalDate.now(SEOUL_ZONE).minusDays(1);

        assertError(
                () -> tripService.createTrip(
                        USER_PUBLIC_ID.toString(),
                        request(yesterday, null)
                ),
                ErrorCode.INVALID_REQUEST
        );

        verify(tripRepository, never()).save(any());
    }

    @Test
    void rejectsSurveyDeadlineBeforeToday() {
        LocalDate today = LocalDate.now(SEOUL_ZONE);

        assertError(
                () -> tripService.createTrip(
                        USER_PUBLIC_ID.toString(),
                        request(today.plusDays(5), today.minusDays(1))
                ),
                ErrorCode.INVALID_REQUEST
        );
    }

    @Test
    void rejectsSurveyDeadlineOnTripDate() {
        LocalDate startDate = LocalDate.now(SEOUL_ZONE).plusDays(5);

        assertError(
                () -> tripService.createTrip(
                        USER_PUBLIC_ID.toString(),
                        request(startDate, startDate)
                ),
                ErrorCode.INVALID_REQUEST
        );
    }

    @Test
    void rejectsInactiveUser() {
        when(userRepository.findByPublicIdAndDeletedAtIsNull(USER_PUBLIC_ID))
                .thenReturn(Optional.empty());

        assertError(
                () -> tripService.createTrip(
                        USER_PUBLIC_ID.toString(),
                        request(LocalDate.now(SEOUL_ZONE).plusDays(5), null)
                ),
                ErrorCode.AUTHENTICATION_REQUIRED
        );
    }

    @Test
    void rejectsMissingSubRegion() {
        when(subRegionRepository.findById(1L)).thenReturn(Optional.empty());

        assertError(
                () -> tripService.createTrip(
                        USER_PUBLIC_ID.toString(),
                        request(LocalDate.now(SEOUL_ZONE).plusDays(5), null)
                ),
                ErrorCode.RESOURCE_NOT_FOUND
        );
    }

    @Test
    void rejectsDateOverlappingActiveTrip() {
        LocalDate startDate = LocalDate.now(SEOUL_ZONE).plusDays(5);
        when(tripMemberRepository.countActiveTripsOverlapping(
                user,
                startDate,
                startDate
        )).thenReturn(1L);

        assertError(
                () -> tripService.createTrip(
                        USER_PUBLIC_ID.toString(),
                        request(startDate, null)
                ),
                ErrorCode.TRIP_DATE_CONFLICT
        );

        verify(tripRepository, never()).save(any());
        verify(tripMemberRepository, never()).save(any());
    }

    private TripCreateRequest request(
            LocalDate startDate,
            LocalDate surveyDeadlineDate
    ) {
        return new TripCreateRequest(
                "제주 여행",
                1L,
                startDate,
                4,
                surveyDeadlineDate
        );
    }

    private Trip trip(Long id) {
        LocalDate startDate = LocalDate.now(SEOUL_ZONE).plusDays(5);
        Trip trip = new Trip(
                subRegion,
                "제주 여행",
                startDate,
                (byte) 4,
                startDate.minusDays(1).atTime(23, 59, 59)
        );
        ReflectionTestUtils.setField(trip, "id", id);
        return trip;
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
