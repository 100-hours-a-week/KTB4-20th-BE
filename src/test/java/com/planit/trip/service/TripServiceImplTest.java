package com.planit.trip.service;

import com.planit.auth.token.SecureTokenGenerator;
import com.planit.auth.token.TokenHasher;
import com.planit.domain.BroadRegion;
import com.planit.domain.SubRegion;
import com.planit.domain.Trip;
import com.planit.domain.TripInvitation;
import com.planit.domain.TripMember;
import com.planit.domain.TripMemberRole;
import com.planit.domain.TripProgressStatus;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.image.config.ImageProperties;
import com.planit.repository.SubRegionRepository;
import com.planit.repository.TripInvitationRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.trip.dto.TripCreateRequest;
import com.planit.trip.dto.TripCreateResponse;
import com.planit.trip.dto.TripDetailResponse;
import com.planit.trip.dto.TripJoinRequest;
import com.planit.trip.dto.TripJoinResponse;
import com.planit.trip.dto.TripListResponse;
import com.planit.trip.pagination.TripListCursorCodec;
import com.planit.trip.pagination.TripListCursorCodec.Cursor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    private TripListCursorCodec tripListCursorCodec;
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
        tripListCursorCodec = new TripListCursorCodec();
        tripService = new TripServiceImpl(
                userRepository,
                subRegionRepository,
                tripRepository,
                tripMemberRepository,
                tripInvitationRepository,
                secureTokenGenerator,
                tokenHasher,
                tripListCursorCodec,
                new ImageProperties(URI.create(
                        "https://example.com/default-profile.png"
                ))
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
    void returnsRequestedTripsAndNextCursor() {
        LocalDate referenceDate = LocalDate.now(SEOUL_ZONE);
        Trip firstTrip = trip(101L, referenceDate.plusDays(1));
        Trip secondTrip = trip(102L, referenceDate.plusDays(2));
        Trip extraTrip = trip(103L, referenceDate.plusDays(3));

        TripMember firstMembership =
                TripMember.createMember(firstTrip, user);
        TripMember secondMembership =
                TripMember.createMember(secondTrip, user);
        TripMember extraMembership =
                TripMember.createMember(extraTrip, user);

        when(user.getUsername()).thenReturn("사용자A");
        when(tripMemberRepository.findActiveTripMemberships(
                org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.eq(referenceDate),
                any()
        )).thenReturn(List.of(
                firstMembership,
                secondMembership,
                extraMembership
        ));
        when(tripMemberRepository.findActiveMembersByTripIds(
                List.of(101L, 102L)
        )).thenReturn(List.of(firstMembership, secondMembership));

        TripListResponse response = tripService.getTrips(
                USER_PUBLIC_ID.toString(),
                null,
                2
        );

        assertThat(response.trips()).hasSize(2);
        assertThat(response.trips().getFirst().tripId())
                .isEqualTo("101");
        assertThat(response.trips().getFirst().members().getFirst().userName())
                .isEqualTo("사용자A");
        assertThat(response.trips().getFirst().members().getFirst()
                .profileImageUrl())
                .isEqualTo("https://example.com/default-profile.png");
        assertThat(response.hasNext()).isTrue();

        Cursor nextCursor = tripListCursorCodec.decode(
                response.nextCursor()
        );
        assertThat(nextCursor).isEqualTo(new Cursor(
                referenceDate,
                referenceDate.plusDays(2)
        ));
    }

    @Test
    void returnsTripsAfterCursor() {
        LocalDate referenceDate = LocalDate.now(SEOUL_ZONE);
        Cursor cursor = new Cursor(
                referenceDate,
                referenceDate.plusDays(1)
        );
        Trip nextTrip = trip(102L, referenceDate.plusDays(2));
        TripMember nextMembership =
                TripMember.createMember(nextTrip, user);

        when(tripMemberRepository.findActiveTripMembershipsAfter(
                user,
                referenceDate,
                0,
                cursor.startDate(),
                org.springframework.data.domain.PageRequest.of(0, 6)
        )).thenReturn(List.of(nextMembership));
        when(tripMemberRepository.findActiveMembersByTripIds(
                List.of(102L)
        )).thenReturn(List.of(nextMembership));

        TripListResponse response = tripService.getTrips(
                USER_PUBLIC_ID.toString(),
                tripListCursorCodec.encode(cursor),
                5
        );

        assertThat(response.trips())
                .extracting(tripResponse -> tripResponse.tripId())
                .containsExactly("102");
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void returnsProgressStatusForEachTrip() {
        LocalDate today = LocalDate.now(SEOUL_ZONE);
        Trip surveyTrip = trip(101L, today.plusDays(1));
        Trip scheduledTrip = trip(102L, today.plusDays(2));
        Trip ongoingTrip = trip(103L, today);
        Trip completedTrip = trip(104L, today.minusDays(1));

        List<TripMember> memberships = List.of(
                TripMember.createMember(surveyTrip, user),
                TripMember.createMember(scheduledTrip, user),
                TripMember.createMember(ongoingTrip, user),
                TripMember.createMember(completedTrip, user)
        );

        when(tripMemberRepository.findActiveTripMemberships(
                org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.eq(today),
                any()
        )).thenReturn(memberships);
        when(tripMemberRepository.findActiveMembersByTripIds(
                List.of(101L, 102L, 103L, 104L)
        )).thenReturn(memberships);
        when(tripRepository.findIdsWithActiveConfirmedSchedule(
                List.of(101L, 102L, 103L, 104L)
        )).thenReturn(List.of(102L));

        TripListResponse response = tripService.getTrips(
                USER_PUBLIC_ID.toString(),
                null,
                4
        );

        assertThat(response.trips())
                .extracting(TripListResponse.TripSummary::status)
                .containsExactly(
                        TripProgressStatus.SURVEY_IN_PROGRESS,
                        TripProgressStatus.SCHEDULE_COMPLETED,
                        TripProgressStatus.TRIP_IN_PROGRESS,
                        TripProgressStatus.TRIP_COMPLETED
                );
    }

    @Test
    void returnsEmptyTripList() {
        when(tripMemberRepository.findActiveTripMemberships(
                org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(List.of());

        TripListResponse response = tripService.getTrips(
                USER_PUBLIC_ID.toString(),
                null,
                10
        );

        assertThat(response.trips()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void rejectsInvalidTripListSize() {
        assertError(
                () -> tripService.getTrips(
                        USER_PUBLIC_ID.toString(),
                        null,
                        11
                ),
                ErrorCode.INVALID_REQUEST
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void findsTripUsingInvitationToken() {
        Trip invitedTrip = trip(200L);
        stubInvitation(invitedTrip);

        TripJoinResponse response = tripService.joinTrip(
                USER_PUBLIC_ID.toString(),
                new TripJoinRequest(INVITATION_TOKEN)
        );

        assertThat(response.tripId()).isEqualTo("200");
        verify(tokenHasher).sha256(INVITATION_TOKEN);
        verify(tripInvitationRepository)
                .findByTokenHash(INVITATION_TOKEN_HASH);
        verify(tripRepository).findByIdForUpdate(200L);

        ArgumentCaptor<TripMember> memberCaptor =
                ArgumentCaptor.forClass(TripMember.class);
        verify(tripMemberRepository).save(memberCaptor.capture());
        TripMember member = memberCaptor.getValue();
        assertThat(member.getTrip()).isSameAs(invitedTrip);
        assertThat(member.getUser()).isSameAs(user);
        assertThat(member.getRole()).isEqualTo(TripMemberRole.MEMBER);
    }

    @Test
    void returnsTripDetailWithActiveMembers() {
        Trip trip = trip(200L);
        BroadRegion broadRegion = mock(BroadRegion.class);
        User memberUser = mock(User.class);
        TripMember host = TripMember.createHost(trip, user);
        TripMember member = TripMember.createMember(trip, memberUser);

        when(subRegion.getId()).thenReturn(123L);
        when(subRegion.getCode()).thenReturn("26350");
        when(subRegion.getName()).thenReturn("해운대구");
        when(subRegion.getBroadRegion()).thenReturn(broadRegion);
        when(broadRegion.getCode()).thenReturn("26");
        when(broadRegion.getName()).thenReturn("부산광역시");
        when(user.getPublicId()).thenReturn(USER_PUBLIC_ID);
        when(user.getUsername()).thenReturn("사용자A");
        when(memberUser.getPublicId()).thenReturn(UUID.fromString(
                "01991f6e-7300-7b21-a3cc-1436db3df95f"
        ));
        when(memberUser.getUsername()).thenReturn("사용자B");
        when(tripRepository.findById(200L)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.of(host));
        when(tripMemberRepository.findActiveMembersByTrip(trip))
                .thenReturn(List.of(host, member));

        TripDetailResponse response = tripService.getTripDetail(
                USER_PUBLIC_ID.toString(),
                200L
        );

        assertThat(response.tripId()).isEqualTo("200");
        assertThat(response.region().broadRegionName())
                .isEqualTo("부산광역시");
        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.myRole()).isEqualTo(TripMemberRole.HOST);
        assertThat(response.members())
                .extracting(TripDetailResponse.Member::userName)
                .containsExactly("사용자A", "사용자B");
        assertThat(response.members())
                .extracting(TripDetailResponse.Member::profileImageUrl)
                .containsOnly("https://example.com/default-profile.png");
    }

    @Test
    void rejectsTripDetailForNonMember() {
        Trip trip = trip(200L);
        when(tripRepository.findById(200L)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.empty());

        assertError(
                () -> tripService.getTripDetail(
                        USER_PUBLIC_ID.toString(),
                        200L
                ),
                ErrorCode.TRIP_MEMBER_REQUIRED
        );
    }

    @Test
    void rejectsMissingTripDetail() {
        when(tripRepository.findById(200L)).thenReturn(Optional.empty());

        assertError(
                () -> tripService.getTripDetail(
                        USER_PUBLIC_ID.toString(),
                        200L
                ),
                ErrorCode.TRIP_NOT_FOUND
        );
    }

    @Test
    void rejectsDeletedTripDetail() {
        Trip trip = trip(200L);
        ReflectionTestUtils.setField(
                trip,
                "deletedAt",
                java.time.LocalDateTime.now()
        );
        when(tripRepository.findById(200L)).thenReturn(Optional.of(trip));

        assertError(
                () -> tripService.getTripDetail(
                        USER_PUBLIC_ID.toString(),
                        200L
                ),
                ErrorCode.TRIP_NOT_FOUND
        );
    }

    @Test
    void rejectsInactiveTripMember() {
        Trip trip = trip(200L);
        TripMember inactiveMember = TripMember.createMember(trip, user);
        ReflectionTestUtils.setField(
                inactiveMember,
                "activeSlot",
                (byte) 0
        );
        when(tripRepository.findById(200L)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.of(inactiveMember));

        assertError(
                () -> tripService.getTripDetail(
                        USER_PUBLIC_ID.toString(),
                        200L
                ),
                ErrorCode.TRIP_MEMBER_REQUIRED
        );
    }

    @Test
    void leavesTripAsMember() {
        Trip trip = trip(200L);
        TripMember member = TripMember.createMember(trip, user);
        when(tripRepository.findByIdForUpdate(200L))
                .thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.of(member));

        tripService.leaveTrip(USER_PUBLIC_ID.toString(), 200L);

        assertThat(member.getActiveSlot()).isEqualTo((byte) 0);
        assertThat(member.getLeftAt()).isNotNull();
        verify(tripRepository).findByIdForUpdate(200L);
        verify(tripMemberRepository, never()).findActiveMembersByTrip(trip);
    }

    @Test
    void rejectsLeavingWhenHostIsAlone() {
        Trip trip = trip(200L);
        TripMember host = TripMember.createHost(trip, user);
        when(tripRepository.findByIdForUpdate(200L))
                .thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.of(host));
        when(tripMemberRepository.findActiveMembersByTrip(trip))
                .thenReturn(List.of(host));

        assertError(
                () -> tripService.leaveTrip(
                        USER_PUBLIC_ID.toString(),
                        200L
                ),
                ErrorCode.HOST_CANNOT_LEAVE_ALONE
        );

        assertThat(host.getActiveSlot()).isEqualTo((byte) 1);
        assertThat(host.getLeftAt()).isNull();
    }

    @Test
    void transfersHostRoleToFirstJoinedMember() {
        Trip trip = trip(200L);
        TripMember host = TripMember.createHost(trip, user);
        TripMember nextHost = TripMember.createMember(
                trip,
                mock(User.class)
        );
        TripMember otherMember = TripMember.createMember(
                trip,
                mock(User.class)
        );
        when(tripRepository.findByIdForUpdate(200L))
                .thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.of(host));
        when(tripMemberRepository.findActiveMembersByTrip(trip))
                .thenReturn(List.of(host, nextHost, otherMember));

        tripService.leaveTrip(USER_PUBLIC_ID.toString(), 200L);

        assertThat(host.getActiveSlot()).isEqualTo((byte) 0);
        assertThat(host.getHostSlot()).isNull();
        assertThat(host.getLeftAt()).isNotNull();
        assertThat(nextHost.getRole()).isEqualTo(TripMemberRole.HOST);
        assertThat(nextHost.getHostSlot()).isEqualTo((byte) 1);
        assertThat(otherMember.getRole()).isEqualTo(TripMemberRole.MEMBER);
        verify(tripMemberRepository).flush();
    }

    @Test
    void rejectsLeavingTripForNonMember() {
        Trip trip = trip(200L);
        when(tripRepository.findByIdForUpdate(200L))
                .thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.empty());

        assertError(
                () -> tripService.leaveTrip(
                        USER_PUBLIC_ID.toString(),
                        200L
                ),
                ErrorCode.TRIP_MEMBER_REQUIRED
        );
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
        stubInvitation(deletedTrip);

        assertError(
                () -> tripService.joinTrip(
                        USER_PUBLIC_ID.toString(),
                        new TripJoinRequest(INVITATION_TOKEN)
                ),
                ErrorCode.RESOURCE_NOT_FOUND
        );
    }

    @Test
    void rejectsInvitationForPastTrip() {
        Trip pastTrip = trip(200L);
        LocalDate yesterday = LocalDate.now(SEOUL_ZONE).minusDays(1);
        ReflectionTestUtils.setField(pastTrip, "startDate", yesterday);
        ReflectionTestUtils.setField(pastTrip, "endDate", yesterday);
        stubInvitation(pastTrip);

        assertError(
                () -> tripService.joinTrip(
                        USER_PUBLIC_ID.toString(),
                        new TripJoinRequest(INVITATION_TOKEN)
                ),
                ErrorCode.RESOURCE_NOT_FOUND
        );
    }

    @Test
    void rejectsUserAlreadyParticipatingInTrip() {
        Trip invitedTrip = trip(200L);
        stubInvitation(invitedTrip);
        when(tripMemberRepository
                .existsByTripAndUserAndLeftAtIsNull(invitedTrip, user))
                .thenReturn(true);

        assertError(
                () -> tripService.joinTrip(
                        USER_PUBLIC_ID.toString(),
                        new TripJoinRequest(INVITATION_TOKEN)
                ),
                ErrorCode.TRIP_ALREADY_JOINED
        );
    }

    @Test
    void rejectsTripAtCapacity() {
        Trip invitedTrip = trip(200L);
        stubInvitation(invitedTrip);
        when(tripMemberRepository.countByTripAndLeftAtIsNull(invitedTrip))
                .thenReturn(4L);

        assertError(
                () -> tripService.joinTrip(
                        USER_PUBLIC_ID.toString(),
                        new TripJoinRequest(INVITATION_TOKEN)
                ),
                ErrorCode.TRIP_CAPACITY_EXCEEDED
        );
    }

    @Test
    void rejectsJoiningTripWithOverlappingDate() {
        Trip invitedTrip = trip(200L);
        stubInvitation(invitedTrip);
        when(tripMemberRepository.countActiveTripsOverlapping(
                user,
                invitedTrip.getStartDate(),
                invitedTrip.getEndDate()
        )).thenReturn(1L);

        assertError(
                () -> tripService.joinTrip(
                        USER_PUBLIC_ID.toString(),
                        new TripJoinRequest(INVITATION_TOKEN)
                ),
                ErrorCode.TRIP_DATE_CONFLICT
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
        return trip(id, startDate);
    }

    private Trip trip(Long id, LocalDate startDate) {
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

    private void stubInvitation(Trip trip) {
        when(tripInvitationRepository.findByTokenHash(
                INVITATION_TOKEN_HASH
        )).thenReturn(Optional.of(new TripInvitation(
                trip,
                INVITATION_TOKEN_HASH
        )));
        when(tripRepository.findByIdForUpdate(trip.getId()))
                .thenReturn(Optional.of(trip));
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
