package com.planit.trip.service;

import com.planit.auth.config.AuthProperties;
import com.planit.auth.token.TokenHasher;
import com.planit.domain.ImageFile;
import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.domain.TripInvitation;
import com.planit.domain.TripMember;
import com.planit.domain.TripMemberRole;
import com.planit.domain.TripProgressStatus;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.image.storage.ImageStorage;
import com.planit.repository.RegionRepository;
import com.planit.repository.TripInvitationRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.schedule.service.SchedulePersistenceService;
import com.planit.trip.dto.TripCreateRequest;
import com.planit.trip.dto.TripCreateResponse;
import com.planit.trip.dto.TripDetailResponse;
import com.planit.trip.dto.TripJoinRequest;
import com.planit.trip.dto.TripJoinResponse;
import com.planit.trip.dto.TripInvitationPreviewResponse;
import com.planit.trip.dto.TripLeaveResponse;
import com.planit.trip.dto.TripListResponse;
import com.planit.trip.pagination.TripListCursorCodec;
import com.planit.trip.pagination.TripListCursorCodec.Cursor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private static final String INVITATION_SECRET = "test-secret";

    private AuthProperties authProperties;
    private UserRepository userRepository;
    private RegionRepository regionRepository;
    private TripRepository tripRepository;
    private TripMemberRepository tripMemberRepository;
    private TripInvitationRepository tripInvitationRepository;
    private TokenHasher tokenHasher;
    private TripListCursorCodec tripListCursorCodec;
    private ImageStorage imageStorage;
    private SchedulePersistenceService schedulePersistenceService;
    private TripServiceImpl tripService;
    private User user;
    private Region region;

    @BeforeEach
    void setUp() {
        authProperties = mock(AuthProperties.class);
        AuthProperties.Jwt jwt = mock(AuthProperties.Jwt.class);
        when(authProperties.jwt()).thenReturn(jwt);
        when(jwt.secretBase64()).thenReturn(INVITATION_SECRET);
        userRepository = mock(UserRepository.class);
        regionRepository = mock(RegionRepository.class);
        tripRepository = mock(TripRepository.class);
        tripMemberRepository = mock(TripMemberRepository.class);
        tripInvitationRepository = mock(TripInvitationRepository.class);
        tokenHasher = mock(TokenHasher.class);
        tripListCursorCodec = new TripListCursorCodec();
        imageStorage = mock(ImageStorage.class);
        schedulePersistenceService = mock(SchedulePersistenceService.class);
        tripService = new TripServiceImpl(
                authProperties,
                userRepository,
                regionRepository,
                tripRepository,
                tripMemberRepository,
                new TripMemberAccessService(tripMemberRepository),
                tripInvitationRepository,
                tokenHasher,
                tripListCursorCodec,
                imageStorage,
                schedulePersistenceService
        );

        user = mock(User.class);
        region = mock(Region.class);

        when(userRepository.findByPublicIdAndDeletedAtIsNull(USER_PUBLIC_ID))
                .thenReturn(Optional.of(user));
        when(regionRepository.findById(1L))
                .thenReturn(Optional.of(region));
        when(tripRepository.save(any(Trip.class)))
                .thenAnswer(invocation -> {
                    Trip trip = invocation.getArgument(0);
                    ReflectionTestUtils.setField(trip, "id", 100L);
                    return trip;
                });
        when(tripMemberRepository.save(any(TripMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // 여행방 번호 100의 계산 결과 앞 43자가 INVITATION_TOKEN이 되도록 맞춘다.
        when(tokenHasher.sha256(invitationSource(100L)))
                .thenReturn("a".repeat(64));
        when(tokenHasher.sha256(INVITATION_TOKEN))
                .thenReturn(INVITATION_TOKEN_HASH);
        when(tripInvitationRepository.save(any(TripInvitation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @DisplayName("여행과 방장 멤버십, 초대 정보를 함께 생성한다")
    @Test
    void createsTripHostMembershipAndInvitation() {
        LocalDate today = LocalDate.now(SEOUL_ZONE);
        LocalDate startDate = today.plusDays(5);
        LocalDate endDate = startDate.plusDays(2);
        LocalDate deadlineDate = today.plusDays(1);

        TripCreateResponse response = tripService.createTrip(
                USER_PUBLIC_ID.toString(),
                request(startDate, endDate, deadlineDate)
        );

        ArgumentCaptor<Trip> tripCaptor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(tripCaptor.capture());
        Trip savedTrip = tripCaptor.getValue();
        assertThat(response.tripId()).isEqualTo("100");
        assertThat(savedTrip.getStartDate()).isEqualTo(startDate);
        assertThat(savedTrip.getEndDate()).isEqualTo(endDate);
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

    @DisplayName("여행 이름의 앞뒤 공백을 제거해 저장한다")
    @Test
    void trimsTripNameBeforeSaving() {
        LocalDate startDate = LocalDate.now(SEOUL_ZONE).plusDays(5);
        TripCreateRequest request = new TripCreateRequest(
                "  제주 여행  ",
                1L,
                startDate,
                startDate.plusDays(2),
                4,
                null
        );

        tripService.createTrip(USER_PUBLIC_ID.toString(), request);

        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("제주 여행");
    }

    @DisplayName("정원을 생략하면 기본 정원 4명으로 저장한다")
    @Test
    void usesDefaultCapacityWhenCapacityIsMissing() {
        LocalDate startDate = LocalDate.now(SEOUL_ZONE).plusDays(5);
        TripCreateRequest request = new TripCreateRequest(
                "제주 여행",
                1L,
                startDate,
                startDate.plusDays(2),
                null,
                null
        );

        tripService.createTrip(USER_PUBLIC_ID.toString(), request);

        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());
        assertThat(captor.getValue().getCapacity()).isEqualTo((byte) 4);
    }

    @DisplayName("초대 링크의 여행 미리보기를 조회한다")
    @Test
    void retrievesInvitationPreview() {
        Trip trip = trip(1001L);
        Trip conflictingTrip = trip(1002L, trip.getStartDate());
        TripMember host = TripMember.createHost(trip, user);
        User memberUser = mock(User.class);
        TripMember member = TripMember.createMember(trip, memberUser);
        TripMember conflictingMembership =
                TripMember.createMember(conflictingTrip, user);

        when(region.getId()).thenReturn(3L);
        when(region.getName()).thenReturn("부산");
        when(user.getPublicId()).thenReturn(USER_PUBLIC_ID);
        when(user.getUsername()).thenReturn("플랜잇방장");
        when(memberUser.getUsername()).thenReturn("플랜잇멤버");
        when(tripInvitationRepository.findByTokenHash(
                INVITATION_TOKEN_HASH
        )).thenReturn(Optional.of(new TripInvitation(
                trip,
                INVITATION_TOKEN_HASH
        )));
        when(tripMemberRepository.findActiveMembersByTrip(trip))
                .thenReturn(List.of(host, member));
        when(tripMemberRepository.existsByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(true);
        when(tripMemberRepository.findActiveTripsOverlappingExcept(
                user,
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getId()
        )).thenReturn(List.of(conflictingMembership));

        TripInvitationPreviewResponse response =
                tripService.getInvitationPreview(
                        USER_PUBLIC_ID.toString(),
                        INVITATION_TOKEN
                );

        assertThat(response.trip().tripId()).isEqualTo("1001");
        assertThat(response.trip().region().regionName())
                .isEqualTo("부산");
        assertThat(response.trip().memberCount()).isEqualTo(2);
        assertThat(response.inviter().publicId())
                .isEqualTo(USER_PUBLIC_ID);
        assertThat(response.inviter().userName())
                .isEqualTo("플랜잇방장");
        assertThat(response.members())
                .extracting(TripInvitationPreviewResponse.Member::userName)
                .containsExactly("플랜잇방장", "플랜잇멤버");
        assertThat(response.alreadyJoined()).isTrue();
        assertThat(response.conflictingTrip().tripId())
                .isEqualTo("1002");
    }

    @DisplayName("존재하지 않는 초대 토큰을 거부한다")
    @Test
    void rejectsUnknownInvitation() {
        when(tripInvitationRepository.findByTokenHash(
                INVITATION_TOKEN_HASH
        )).thenReturn(Optional.empty());

        assertError(
                () -> tripService.getInvitationPreview(
                        null,
                        INVITATION_TOKEN
                ),
                ErrorCode.INVITATION_NOT_FOUND
        );

        verifyNoInteractions(userRepository);
    }

    @DisplayName("유효한 초대 링크를 확인한 후 사용자 인증을 요구한다")
    @Test
    void requiresAuthenticationAfterValidInvitationCheck() {
        Trip trip = trip(1001L);
        when(tripInvitationRepository.findByTokenHash(
                INVITATION_TOKEN_HASH
        )).thenReturn(Optional.of(new TripInvitation(
                trip,
                INVITATION_TOKEN_HASH
        )));

        assertError(
                () -> tripService.getInvitationPreview(
                        null,
                        INVITATION_TOKEN
                ),
                ErrorCode.AUTHENTICATION_REQUIRED
        );

        verifyNoInteractions(userRepository);
    }

    @DisplayName("삭제된 여행의 초대 미리보기를 거부한다")
    @Test
    void rejectsPreviewForDeletedTrip() {
        Trip trip = trip(1001L);
        ReflectionTestUtils.setField(
                trip,
                "deletedAt",
                LocalDateTime.now(SEOUL_ZONE)
        );
        when(tripInvitationRepository.findByTokenHash(
                INVITATION_TOKEN_HASH
        )).thenReturn(Optional.of(new TripInvitation(
                trip,
                INVITATION_TOKEN_HASH
        )));

        assertError(
                () -> tripService.getInvitationPreview(
                        null,
                        INVITATION_TOKEN
                ),
                ErrorCode.INVITATION_TRIP_DELETED
        );
    }

    @DisplayName("종료된 여행의 초대 링크를 거부한다")
    @Test
    void rejectsExpiredInvitation() {
        LocalDate yesterday = LocalDate.now(SEOUL_ZONE).minusDays(1);
        Trip trip = trip(
                1001L,
                yesterday.minusDays(2),
                yesterday
        );
        when(tripInvitationRepository.findByTokenHash(
                INVITATION_TOKEN_HASH
        )).thenReturn(Optional.of(new TripInvitation(
                trip,
                INVITATION_TOKEN_HASH
        )));

        assertError(
                () -> tripService.getInvitationPreview(
                        null,
                        INVITATION_TOKEN
                ),
                ErrorCode.INVITATION_EXPIRED
        );
    }

    @DisplayName("설문 마감 후에도 여행 시작 전이면 초대 미리보기를 허용한다")
    @Test
    void allowsInvitationPreviewAfterSurveyDeadline() {
        Trip trip = trip(1001L);
        ReflectionTestUtils.setField(
                trip,
                "surveyDeadlineAt",
                LocalDateTime.now(SEOUL_ZONE).minusMinutes(1)
        );
        when(tripInvitationRepository.findByTokenHash(
                INVITATION_TOKEN_HASH
        )).thenReturn(Optional.of(new TripInvitation(
                trip,
                INVITATION_TOKEN_HASH
        )));

        // 마감으로 막히지 않고 유효한 링크로 판단되어 로그인 확인까지 진행된다.
        assertError(
                () -> tripService.getInvitationPreview(
                        null,
                        INVITATION_TOKEN
                ),
                ErrorCode.AUTHENTICATION_REQUIRED
        );
    }

    @DisplayName("설문 마감 후에도 여행 시작 전이면 초대받은 여행에 참여한다")
    @Test
    void joinsTripAfterSurveyDeadline() {
        Trip invitedTrip = trip(200L);
        ReflectionTestUtils.setField(
                invitedTrip,
                "surveyDeadlineAt",
                LocalDateTime.now(SEOUL_ZONE).minusMinutes(1)
        );
        stubInvitation(invitedTrip);

        TripJoinResponse response = tripService.joinTrip(
                USER_PUBLIC_ID.toString(),
                new TripJoinRequest(INVITATION_TOKEN)
        );

        assertThat(response.tripId()).isEqualTo("200");
        verify(tripMemberRepository).save(any(TripMember.class));
    }

    @DisplayName("요청한 개수의 여행과 다음 커서를 반환한다")
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

        ImageFile profileImage = mock(ImageFile.class);
        when(user.getUsername()).thenReturn("사용자A");
        when(user.getImageFile()).thenReturn(profileImage);
        when(profileImage.getImageKey())
                .thenReturn("profiles/user/profile.jpg");
        when(imageStorage.createReadUrl(
                "profiles/user/profile.jpg"
        )).thenReturn("https://example.com/presigned-profile");
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
        assertThat(response.trips().getFirst().endDate())
                .isEqualTo(referenceDate.plusDays(3));
        assertThat(response.trips().getFirst().members().getFirst().userName())
                .isEqualTo("사용자A");
        assertThat(response.trips().getFirst().members().getFirst()
                .profileImageUrl())
                .isEqualTo("https://example.com/presigned-profile");
        assertThat(response.hasNext()).isTrue();

        Cursor nextCursor = tripListCursorCodec.decode(
                response.nextCursor()
        );
        assertThat(nextCursor).isEqualTo(new Cursor(
                referenceDate,
                1,
                referenceDate.plusDays(2),
                102L
        ));
    }

    @DisplayName("커서 이후의 여행 목록을 반환한다")
    @Test
    void returnsTripsAfterCursor() {
        LocalDate referenceDate = LocalDate.now(SEOUL_ZONE);
        Cursor cursor = new Cursor(
                referenceDate,
                1,
                referenceDate.plusDays(1),
                101L
        );
        Trip nextTrip = trip(102L, referenceDate.plusDays(2));
        TripMember nextMembership =
                TripMember.createMember(nextTrip, user);

        when(tripMemberRepository.findActiveTripMembershipsAfter(
                user,
                referenceDate,
                cursor.sectionOrder(),
                cursor.sortDate(),
                cursor.tripId(),
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

    @DisplayName("여행 시작 전과 여행 기간 및 종료 후의 진행 상태를 계산해 반환한다")
    @Test
    void returnsProgressStatusForEachTrip() {
        LocalDate today = LocalDate.now(SEOUL_ZONE);
        Trip surveyTrip = trip(
                101L,
                today.plusDays(1),
                today.plusDays(3)
        );
        Trip scheduledTrip = trip(
                102L,
                today.plusDays(2),
                today.plusDays(4)
        );
        Trip middleOfTrip = trip(
                103L,
                today.minusDays(1),
                today.plusDays(1)
        );
        Trip endingTodayTrip = trip(
                104L,
                today.minusDays(2),
                today
        );
        Trip completedTrip = trip(
                105L,
                today.minusDays(2),
                today.minusDays(1)
        );

        List<TripMember> memberships = List.of(
                TripMember.createMember(surveyTrip, user),
                TripMember.createMember(scheduledTrip, user),
                TripMember.createMember(middleOfTrip, user),
                TripMember.createMember(endingTodayTrip, user),
                TripMember.createMember(completedTrip, user)
        );

        when(tripMemberRepository.findActiveTripMemberships(
                org.mockito.ArgumentMatchers.eq(user),
                org.mockito.ArgumentMatchers.eq(today),
                any()
        )).thenReturn(memberships);
        when(tripMemberRepository.findActiveMembersByTripIds(
                List.of(101L, 102L, 103L, 104L, 105L)
        )).thenReturn(memberships);
        when(tripRepository.findIdsWithActiveConfirmedSchedule(
                List.of(101L, 102L, 103L, 104L, 105L)
        )).thenReturn(List.of(102L));

        TripListResponse response = tripService.getTrips(
                USER_PUBLIC_ID.toString(),
                null,
                5
        );

        assertThat(response.trips())
                .extracting(TripListResponse.TripSummary::status)
                .containsExactly(
                        TripProgressStatus.SURVEY_IN_PROGRESS,
                        TripProgressStatus.SCHEDULE_COMPLETED,
                        TripProgressStatus.TRIP_IN_PROGRESS,
                        TripProgressStatus.TRIP_IN_PROGRESS,
                        TripProgressStatus.TRIP_COMPLETED
                );
    }

    @DisplayName("참여한 여행이 없으면 빈 목록을 반환한다")
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

    @DisplayName("허용 범위를 벗어난 여행 목록 크기를 거부한다")
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

    @DisplayName("초대 토큰으로 여행을 조회한다")
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

    @DisplayName("활성 멤버를 포함한 여행 상세 정보를 반환한다")
    @Test
    void returnsTripDetailWithActiveMembers() {
        Trip trip = trip(200L);
        User memberUser = mock(User.class);
        TripMember host = TripMember.createHost(trip, user);
        TripMember member = TripMember.createMember(trip, memberUser);
        ReflectionTestUtils.setField(host, "id", 2001L);
        ReflectionTestUtils.setField(member, "id", 2002L);

        when(region.getId()).thenReturn(3L);
        when(region.getCode()).thenReturn("REGION-BUSAN");
        when(region.getName()).thenReturn("부산");
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
        when(tripMemberRepository.findActiveMembersByTripIncludingWithdrawn(trip))
                .thenReturn(List.of(host, member));

        TripDetailResponse response = tripService.getTripDetail(
                USER_PUBLIC_ID.toString(),
                200L
        );

        assertThat(response.tripId()).isEqualTo("200");
        assertThat(response.region().regionName())
                .isEqualTo("부산");
        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.myRole()).isEqualTo(TripMemberRole.HOST);
        assertThat(response.members())
                .extracting(TripDetailResponse.Member::userName)
                .containsExactly("사용자A", "사용자B");
        assertThat(response.members())
                .extracting(TripDetailResponse.Member::profileImageUrl)
                .allMatch(profileImageUrl -> profileImageUrl == null);
    }

    @DisplayName("탈퇴한 멤버는 여행 상세에서 대체 정보로 표시한다")
    @Test
    void showsWithdrawnUserPlaceholderInTripDetail() {
        Trip trip = trip(200L);
        User withdrawnUser = mock(User.class);
        TripMember host = TripMember.createHost(trip, user);
        TripMember withdrawnMember = TripMember.createMember(trip, withdrawnUser);
        ReflectionTestUtils.setField(host, "id", 2001L);
        ReflectionTestUtils.setField(withdrawnMember, "id", 2002L);

        when(region.getId()).thenReturn(3L);
        when(region.getCode()).thenReturn("REGION-BUSAN");
        when(region.getName()).thenReturn("부산");
        when(user.getPublicId()).thenReturn(USER_PUBLIC_ID);
        when(user.getUsername()).thenReturn("사용자A");
        when(withdrawnUser.getUsername()).thenReturn("사용자B");
        when(withdrawnUser.getDeletedAt())
                .thenReturn(LocalDateTime.now(SEOUL_ZONE));
        when(tripRepository.findById(200L)).thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.of(host));
        when(tripMemberRepository.findActiveMembersByTripIncludingWithdrawn(trip))
                .thenReturn(List.of(host, withdrawnMember));

        TripDetailResponse response = tripService.getTripDetail(
                USER_PUBLIC_ID.toString(),
                200L
        );

        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.members())
                .extracting(TripDetailResponse.Member::userName)
                .containsExactly("사용자A", "탈퇴한 사용자");
        assertThat(response.members())
                .extracting(TripDetailResponse.Member::userPublicId)
                .containsExactly(USER_PUBLIC_ID, null);
    }

    @DisplayName("여행 멤버가 아닌 사용자의 상세 조회를 거부한다")
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

    @DisplayName("존재하지 않는 여행의 상세 조회를 거부한다")
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

    @DisplayName("삭제된 여행의 상세 조회를 거부한다")
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

    @DisplayName("탈퇴한 여행 멤버의 상세 조회를 거부한다")
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

    @DisplayName("활성 멤버가 세 명 이상 남으면 일반 멤버만 여행에서 탈퇴시킨다")
    @Test
    void leavesTripAsMemberWhenThreeOrMoreActiveMembersRemain() {
        Trip trip = trip(200L);
        TripMember host = TripMember.createHost(trip, user);
        TripMember member = TripMember.createMember(trip, mock(User.class));
        TripMember otherMember = TripMember.createMember(trip, mock(User.class));
        when(tripRepository.findByIdForUpdate(200L))
                .thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.of(member));
        when(tripMemberRepository.findActiveMembersByTrip(trip))
                .thenReturn(List.of(host, member, otherMember));

        TripLeaveResponse response =
                tripService.leaveTrip(USER_PUBLIC_ID.toString(), 200L);

        assertThat(member.getActiveSlot()).isEqualTo((byte) 0);
        assertThat(member.getLeftAt()).isNotNull();
        assertThat(trip.getDeletedAt()).isNull();
        assertThat(response.tripDeleted()).isFalse();
        assertThat(response.newHostMemberId()).isNull();
        verify(tripMemberRepository, never()).flush();
        verifyNoInteractions(schedulePersistenceService);
    }

    @DisplayName("혼자 남은 방장이 나가면 여행을 삭제한다")
    @Test
    void deletesTripWhenHostLeavesAlone() {
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

        TripLeaveResponse response =
                tripService.leaveTrip(USER_PUBLIC_ID.toString(), 200L);

        assertThat(host.getActiveSlot()).isEqualTo((byte) 0);
        assertThat(host.getLeftAt()).isNotNull();
        assertThat(trip.getDeletedAt()).isNotNull();
        assertThat(response.tripDeleted()).isTrue();
        assertThat(response.newHostMemberId()).isNull();
        verify(tripMemberRepository, never()).flush();
        verify(schedulePersistenceService).deleteAllForTrip(200L);
    }

    @DisplayName("탈퇴 후 한 명만 남으면 여행을 삭제하고 남은 멤버십도 종료한다")
    @Test
    void deletesTripAndEndsRemainingMembershipWhenLeavingDropsToOneMember() {
        // 방장이 나가서 1명만 남으면, 한 번이라도 2명 이상이었던 방이므로 방 전체를 삭제하고
        // 남은 멤버십도 함께 종료한다.
        Trip trip = trip(200L);
        TripMember host = TripMember.createHost(trip, user);
        TripMember remainingMember = TripMember.createMember(
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
                .thenReturn(List.of(host, remainingMember));

        TripLeaveResponse response =
                tripService.leaveTrip(USER_PUBLIC_ID.toString(), 200L);

        assertThat(host.getActiveSlot()).isEqualTo((byte) 0);
        assertThat(remainingMember.getActiveSlot()).isEqualTo((byte) 0);
        assertThat(remainingMember.getLeftAt()).isNotNull();
        assertThat(trip.getDeletedAt()).isNotNull();
        assertThat(response.tripDeleted()).isTrue();
        assertThat(response.newHostMemberId()).isNull();
        verify(schedulePersistenceService).deleteAllForTrip(200L);
    }

    @DisplayName("일반 멤버 탈퇴 후 한 명만 남으면 여행을 삭제한다")
    @Test
    void deletesTripWhenNonHostMemberLeavesDropsToOneMember() {
        // 방장이 아닌 멤버가 나가도, 남는 인원이 1명뿐이면 방 전체가 삭제된다.
        Trip trip = trip(200L);
        TripMember host = TripMember.createHost(trip, mock(User.class));
        TripMember leavingMember = TripMember.createMember(trip, user);
        when(tripRepository.findByIdForUpdate(200L))
                .thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.of(leavingMember));
        when(tripMemberRepository.findActiveMembersByTrip(trip))
                .thenReturn(List.of(host, leavingMember));

        TripLeaveResponse response =
                tripService.leaveTrip(USER_PUBLIC_ID.toString(), 200L);

        assertThat(leavingMember.getActiveSlot()).isEqualTo((byte) 0);
        assertThat(host.getActiveSlot()).isEqualTo((byte) 0);
        assertThat(host.getLeftAt()).isNotNull();
        assertThat(trip.getDeletedAt()).isNotNull();
        assertThat(response.tripDeleted()).isTrue();
        verify(schedulePersistenceService).deleteAllForTrip(200L);
    }

    @DisplayName("방장이 나가면 가장 먼저 참여한 멤버에게 방장을 위임한다")
    @Test
    void transfersHostRoleToEarliestJoinedMember() {
        Trip trip = trip(200L);
        TripMember host = TripMember.createHost(trip, user);
        TripMember earlierMember = TripMember.createMember(
                trip,
                mock(User.class)
        );
        TripMember laterMember = TripMember.createMember(
                trip,
                mock(User.class)
        );
        ReflectionTestUtils.setField(earlierMember, "id", 2001L);
        when(tripRepository.findByIdForUpdate(200L))
                .thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.of(host));
        // findActiveMembersByTrip은 joinedAt 오름차순이라, 목록의 첫 번째가 가장 먼저 참가한 멤버다.
        when(tripMemberRepository.findActiveMembersByTrip(trip))
                .thenReturn(List.of(host, earlierMember, laterMember));

        TripLeaveResponse response =
                tripService.leaveTrip(USER_PUBLIC_ID.toString(), 200L);

        assertThat(host.getActiveSlot()).isEqualTo((byte) 0);
        assertThat(host.getHostSlot()).isNull();
        assertThat(host.getLeftAt()).isNotNull();
        assertThat(earlierMember.getRole()).isEqualTo(TripMemberRole.HOST);
        assertThat(earlierMember.getHostSlot()).isEqualTo((byte) 1);
        assertThat(laterMember.getRole()).isEqualTo(TripMemberRole.MEMBER);
        assertThat(trip.getDeletedAt()).isNull();
        assertThat(response.tripDeleted()).isFalse();
        assertThat(response.newHostMemberId()).isEqualTo("2001");
        verify(tripMemberRepository).flush();
        verifyNoInteractions(schedulePersistenceService);
    }

    @DisplayName("이미 시작한 여행에서 나가기를 거부한다")
    @Test
    void rejectsLeavingTripThatAlreadyStarted() {
        Trip trip = trip(200L, LocalDate.now(SEOUL_ZONE));
        TripMember host = TripMember.createHost(trip, user);
        when(tripRepository.findByIdForUpdate(200L))
                .thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.of(host));

        assertError(
                () -> tripService.leaveTrip(USER_PUBLIC_ID.toString(), 200L),
                ErrorCode.TRIP_LEAVE_NOT_ALLOWED
        );

        verify(tripMemberRepository, never()).findActiveMembersByTrip(trip);
    }

    @DisplayName("사용자 탈퇴 시 여러 여행의 방장을 위임하고 1인 여행은 삭제한다")
    @Test
    void withdrawalTransfersHostAndDeletesSoloTripAcrossMultipleTrips() {
        // 탈퇴하는 사용자가 방장으로 있는 여행방 두 곳: 하나는 다른 멤버가 2명 있어 위임되고,
        // 다른 하나는 혼자라서 삭제된다. 날짜 제한은 탈퇴 정리에는 적용되지 않는다.
        Trip tripWithOtherMembers = trip(200L);
        TripMember hostOfFirstTrip = TripMember.createHost(tripWithOtherMembers, user);
        TripMember nextHost = TripMember.createMember(
                tripWithOtherMembers,
                mock(User.class)
        );
        TripMember otherMember = TripMember.createMember(
                tripWithOtherMembers,
                mock(User.class)
        );
        ReflectionTestUtils.setField(nextHost, "id", 2001L);

        Trip soloTrip = trip(300L);
        TripMember soloHost = TripMember.createHost(soloTrip, user);

        LocalDateTime withdrawnAt = LocalDateTime.now(SEOUL_ZONE);

        when(tripMemberRepository.findByUserAndActiveSlotAndLeftAtIsNull(
                user,
                (byte) 1
        )).thenReturn(List.of(hostOfFirstTrip, soloHost));
        when(tripRepository.findByIdForUpdate(200L))
                .thenReturn(Optional.of(tripWithOtherMembers));
        when(tripRepository.findByIdForUpdate(300L))
                .thenReturn(Optional.of(soloTrip));
        when(tripMemberRepository.findActiveMembersByTrip(tripWithOtherMembers))
                .thenReturn(List.of(hostOfFirstTrip, nextHost, otherMember));
        when(tripMemberRepository.findActiveMembersByTrip(soloTrip))
                .thenReturn(List.of(soloHost));

        tripService.leaveAllTripsForWithdrawal(user, withdrawnAt);

        assertThat(hostOfFirstTrip.getLeftAt()).isEqualTo(withdrawnAt);
        assertThat(nextHost.getRole()).isEqualTo(TripMemberRole.HOST);
        assertThat(tripWithOtherMembers.getDeletedAt()).isNull();

        assertThat(soloHost.getLeftAt()).isEqualTo(withdrawnAt);
        assertThat(soloTrip.getDeletedAt()).isEqualTo(withdrawnAt);
        verify(schedulePersistenceService).deleteAllForTrip(300L);
    }

    @DisplayName("종료된 여행의 유일한 멤버가 탈퇴해도 완료된 여행 기록을 유지한다")
    @Test
    void withdrawalKeepsMembershipAliveWhenSoleActiveTripAlreadyEnded() {
        // 이미 끝난 여행에 혼자 남아있던 방장이 탈퇴해도, 완료된 여행 기록은 삭제하지 않고
        // 멤버십도 종료 처리하지 않는다(화면에서는 User.deletedAt 기준으로 "탈퇴한 사용자"로
        // 표시된다).
        LocalDate endedStartDate = LocalDate.now(SEOUL_ZONE).minusDays(3);
        Trip endedTrip = trip(200L, endedStartDate);
        TripMember soloHost = TripMember.createHost(endedTrip, user);
        LocalDateTime withdrawnAt = LocalDateTime.now(SEOUL_ZONE);

        when(tripMemberRepository.findByUserAndActiveSlotAndLeftAtIsNull(
                user,
                (byte) 1
        )).thenReturn(List.of(soloHost));
        when(tripRepository.findByIdForUpdate(200L))
                .thenReturn(Optional.of(endedTrip));
        when(tripMemberRepository.findActiveMembersByTrip(endedTrip))
                .thenReturn(List.of(soloHost));

        tripService.leaveAllTripsForWithdrawal(user, withdrawnAt);

        assertThat(soloHost.getLeftAt()).isNull();
        assertThat(soloHost.getActiveSlot()).isEqualTo((byte) 1);
        assertThat(soloHost.getHostSlot()).isEqualTo((byte) 1);
        assertThat(endedTrip.getDeletedAt()).isNull();
        verifyNoInteractions(schedulePersistenceService);
    }

    @DisplayName("진행 중인 여행의 방장이 탈퇴하면 여행을 삭제하지 않고 방장을 위임한다")
    @Test
    void withdrawalTransfersHostWithoutDeletingTripInProgress() {
        // 진행 중인(오늘 시작하는) 여행에서 방장이 탈퇴하면, 남은 인원이 1명이어도 방을
        // 삭제하지 않고 그 멤버에게 방장을 이전한다. 탈퇴한 방장의 멤버십 자체는 남는다.
        LocalDate todayStartDate = LocalDate.now(SEOUL_ZONE);
        Trip inProgressTrip = trip(200L, todayStartDate);
        TripMember host = TripMember.createHost(inProgressTrip, user);
        TripMember remainingMember = TripMember.createMember(
                inProgressTrip,
                mock(User.class)
        );
        ReflectionTestUtils.setField(remainingMember, "id", 2002L);
        LocalDateTime withdrawnAt = LocalDateTime.now(SEOUL_ZONE);

        when(tripMemberRepository.findByUserAndActiveSlotAndLeftAtIsNull(
                user,
                (byte) 1
        )).thenReturn(List.of(host));
        when(tripRepository.findByIdForUpdate(200L))
                .thenReturn(Optional.of(inProgressTrip));
        when(tripMemberRepository.findActiveMembersByTrip(inProgressTrip))
                .thenReturn(List.of(host, remainingMember));

        tripService.leaveAllTripsForWithdrawal(user, withdrawnAt);

        assertThat(host.getLeftAt()).isNull();
        assertThat(host.getActiveSlot()).isEqualTo((byte) 1);
        assertThat(host.getHostSlot()).isNull();
        assertThat(host.getRole()).isEqualTo(TripMemberRole.HOST);
        assertThat(remainingMember.getRole()).isEqualTo(TripMemberRole.HOST);
        assertThat(remainingMember.getHostSlot()).isEqualTo((byte) 1);
        assertThat(inProgressTrip.getDeletedAt()).isNull();
        verifyNoInteractions(schedulePersistenceService);
    }

    @DisplayName("종료된 여행에서 일반 멤버가 탈퇴해도 다른 멤버의 여행 기록을 유지한다")
    @Test
    void withdrawalPreservesOtherMembersHistoryWhenNonHostLeavesEndedTrip() {
        // 이미 끝난 여행에서 방장이 아닌 멤버가 탈퇴해도, 방장의 완료된 여행 기록은
        // 본인 의사와 무관하게 사라지지 않는다. 탈퇴한 멤버 본인의 멤버십도 그대로 남는다.
        LocalDate endedStartDate = LocalDate.now(SEOUL_ZONE).minusDays(10);
        Trip endedTrip = trip(200L, endedStartDate);
        TripMember host = TripMember.createHost(endedTrip, mock(User.class));
        TripMember withdrawingMember = TripMember.createMember(endedTrip, user);
        LocalDateTime withdrawnAt = LocalDateTime.now(SEOUL_ZONE);

        when(tripMemberRepository.findByUserAndActiveSlotAndLeftAtIsNull(
                user,
                (byte) 1
        )).thenReturn(List.of(withdrawingMember));
        when(tripRepository.findByIdForUpdate(200L))
                .thenReturn(Optional.of(endedTrip));
        when(tripMemberRepository.findActiveMembersByTrip(endedTrip))
                .thenReturn(List.of(host, withdrawingMember));

        tripService.leaveAllTripsForWithdrawal(user, withdrawnAt);

        assertThat(withdrawingMember.getLeftAt()).isNull();
        assertThat(withdrawingMember.getActiveSlot()).isEqualTo((byte) 1);
        assertThat(host.getLeftAt()).isNull();
        assertThat(host.getActiveSlot()).isEqualTo((byte) 1);
        assertThat(host.getHostSlot()).isEqualTo((byte) 1);
        assertThat(endedTrip.getDeletedAt()).isNull();
        verifyNoInteractions(schedulePersistenceService);
    }

    @DisplayName("방장이 초대 링크를 다시 요청하면 동일한 토큰을 반환한다")
    @Test
    void returnsSameInvitationTokenForHost() {
        Trip trip = trip(100L);
        TripMember host = TripMember.createHost(trip, user);
        when(tripRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.of(host));
        when(tripInvitationRepository.findByTokenHash(
                INVITATION_TOKEN_HASH
        )).thenReturn(Optional.of(new TripInvitation(
                trip,
                INVITATION_TOKEN_HASH
        )));

        TripCreateResponse first = tripService.getInvitation(
                USER_PUBLIC_ID.toString(),
                100L
        );
        TripCreateResponse second = tripService.getInvitation(
                USER_PUBLIC_ID.toString(),
                100L
        );

        assertThat(first.tripId()).isEqualTo("100");
        assertThat(first.invitationToken()).isEqualTo(INVITATION_TOKEN);
        assertThat(second.invitationToken())
                .isEqualTo(first.invitationToken());
        verify(tripInvitationRepository, never())
                .save(any(TripInvitation.class));
    }

    @DisplayName("초대 토큰이 없는 기존 여행에 새 초대 토큰을 발급하고 저장한다")
    @Test
    void savesInvitationHashForTripCreatedBeforeFixedToken() {
        Trip trip = trip(100L);
        TripMember host = TripMember.createHost(trip, user);
        when(tripRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.of(host));
        when(tripInvitationRepository.findByTokenHash(
                INVITATION_TOKEN_HASH
        )).thenReturn(Optional.empty());

        TripCreateResponse response = tripService.getInvitation(
                USER_PUBLIC_ID.toString(),
                100L
        );

        assertThat(response.invitationToken()).isEqualTo(INVITATION_TOKEN);
        ArgumentCaptor<TripInvitation> invitationCaptor =
                ArgumentCaptor.forClass(TripInvitation.class);
        verify(tripInvitationRepository).save(invitationCaptor.capture());
        assertThat(invitationCaptor.getValue().getTrip()).isSameAs(trip);
        assertThat(invitationCaptor.getValue().getTokenHash())
                .isEqualTo(INVITATION_TOKEN_HASH);
    }

    @DisplayName("일반 멤버의 초대 링크 요청을 거부한다")
    @Test
    void rejectsInvitationRequestFromMember() {
        Trip trip = trip(100L);
        TripMember member = TripMember.createMember(trip, user);
        when(tripRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(trip));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                trip,
                user
        )).thenReturn(Optional.of(member));

        assertError(
                () -> tripService.getInvitation(
                        USER_PUBLIC_ID.toString(),
                        100L
                ),
                ErrorCode.ACCESS_DENIED
        );

        verify(tripInvitationRepository, never())
                .save(any(TripInvitation.class));
    }

    @DisplayName("여행 멤버가 아닌 사용자의 나가기를 거부한다")
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

    @DisplayName("존재하지 않는 초대 토큰을 거부한다")
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

    @DisplayName("삭제된 여행의 초대 토큰을 거부한다")
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

    @DisplayName("종료된 여행의 초대 토큰을 거부한다")
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

    @DisplayName("이미 참여 중인 사용자의 중복 참여를 거부한다")
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

    @DisplayName("정원이 가득 찬 여행 참여를 거부한다")
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

    @DisplayName("기존 여행과 일정이 겹치면 참여를 거부한다")
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

    @DisplayName("설문 마감일을 생략하면 여행 전날로 설정한다")
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

    @DisplayName("오늘 날짜의 여행 생성을 거부한다")
    @Test
    void rejectsTodayTripDate() {
        LocalDate today = LocalDate.now(SEOUL_ZONE);

        assertError(
                () -> tripService.createTrip(
                        USER_PUBLIC_ID.toString(),
                        request(today, null)
                ),
                ErrorCode.INVALID_REQUEST
        );

        verify(tripRepository, never()).save(any());
    }

    @DisplayName("과거 날짜의 여행 생성을 거부한다")
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

    @DisplayName("오늘보다 이전인 설문 마감일을 거부한다")
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

    @DisplayName("여행 시작일과 같은 설문 마감일을 거부한다")
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

    @DisplayName("비활성 사용자의 여행 생성을 거부한다")
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

    @DisplayName("존재하지 않는 지역으로 여행을 생성할 수 없다")
    @Test
    void rejectsMissingRegion() {
        when(regionRepository.findById(1L)).thenReturn(Optional.empty());

        assertError(
                () -> tripService.createTrip(
                        USER_PUBLIC_ID.toString(),
                        request(LocalDate.now(SEOUL_ZONE).plusDays(5), null)
                ),
                ErrorCode.RESOURCE_NOT_FOUND
        );
    }

    @DisplayName("참여 중인 여행과 일정이 겹치면 여행 생성을 거부한다")
    @Test
    void rejectsDateOverlappingActiveTrip() {
        LocalDate startDate = LocalDate.now(SEOUL_ZONE).plusDays(5);
        LocalDate endDate = startDate.plusDays(2);
        when(tripMemberRepository.countActiveTripsOverlapping(
                user,
                startDate,
                endDate
        )).thenReturn(1L);

        assertError(
                () -> tripService.createTrip(
                        USER_PUBLIC_ID.toString(),
                        request(startDate, endDate, null)
                ),
                ErrorCode.TRIP_DATE_CONFLICT
        );

        verify(tripRepository, never()).save(any());
        verify(tripMemberRepository, never()).save(any());
    }

    @DisplayName("종료일이 시작일보다 빠른 여행 생성을 거부한다")
    @Test
    void rejectsEndDateBeforeStartDate() {
        LocalDate startDate = LocalDate.now(SEOUL_ZONE).plusDays(5);

        assertError(
                () -> tripService.createTrip(
                        USER_PUBLIC_ID.toString(),
                        request(startDate, startDate.minusDays(1), null)
                ),
                ErrorCode.INVALID_REQUEST
        );

        verify(tripRepository, never()).save(any());
    }

    @DisplayName("10일을 초과하는 여행 생성을 거부한다")
    @Test
    void rejectsTripLongerThanTenDays() {
        LocalDate startDate = LocalDate.now(SEOUL_ZONE).plusDays(5);

        assertError(
                () -> tripService.createTrip(
                        USER_PUBLIC_ID.toString(),
                        request(startDate, startDate.plusDays(10), null)
                ),
                ErrorCode.INVALID_REQUEST
        );

        verify(tripRepository, never()).save(any());
    }

    @DisplayName("10일 여행 생성을 허용한다")
    @Test
    void allowsTenDayTrip() {
        LocalDate startDate = LocalDate.now(SEOUL_ZONE).plusDays(5);
        LocalDate endDate = startDate.plusDays(9);

        tripService.createTrip(
                USER_PUBLIC_ID.toString(),
                request(startDate, endDate, null)
        );

        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());
        assertThat(captor.getValue().getEndDate()).isEqualTo(endDate);
    }

    @DisplayName("시작일과 종료일이 같은 당일 여행 생성을 허용한다")
    @Test
    void allowsDayTrip() {
        LocalDate tripDate = LocalDate.now(SEOUL_ZONE).plusDays(5);

        tripService.createTrip(
                USER_PUBLIC_ID.toString(),
                request(tripDate, tripDate, null)
        );

        ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
        verify(tripRepository).save(captor.capture());
        assertThat(captor.getValue().getStartDate()).isEqualTo(tripDate);
        assertThat(captor.getValue().getEndDate()).isEqualTo(tripDate);
    }

    private TripCreateRequest request(
            LocalDate startDate,
            LocalDate surveyDeadlineDate
    ) {
        return request(
                startDate,
                startDate.plusDays(2),
                surveyDeadlineDate
        );
    }

    private TripCreateRequest request(
            LocalDate startDate,
            LocalDate endDate,
            LocalDate surveyDeadlineDate
    ) {
        return new TripCreateRequest(
                "제주 여행",
                1L,
                startDate,
                endDate,
                4,
                surveyDeadlineDate
        );
    }

    private Trip trip(Long id) {
        LocalDate startDate = LocalDate.now(SEOUL_ZONE).plusDays(5);
        return trip(id, startDate);
    }

    private Trip trip(Long id, LocalDate startDate) {
        return trip(id, startDate, startDate.plusDays(2));
    }

    private Trip trip(
            Long id,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Trip trip = new Trip(
                region,
                "제주 여행",
                startDate,
                endDate,
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

    private String invitationSource(Long tripId) {
        return INVITATION_SECRET + ":trip-invitation:" + tripId;
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception ->
                        ((BusinessException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
