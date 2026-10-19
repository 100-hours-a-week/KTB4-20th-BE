package com.planit.schedule.service;

import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.schedule.domain.Place;
import com.planit.schedule.domain.PlaceDetails;
import com.planit.schedule.domain.Schedule;
import com.planit.schedule.domain.ScheduleDay;
import com.planit.schedule.domain.ScheduleLeg;
import com.planit.schedule.domain.ScheduleStrategy;
import com.planit.schedule.domain.ScheduleVisit;
import com.planit.schedule.dto.ScheduleCandidateDetailResponse;
import com.planit.schedule.dto.ScheduleCandidateListResponse;
import com.planit.schedule.dto.ScheduleConfirmRequest;
import com.planit.schedule.dto.ScheduleConfirmResponse;
import com.planit.schedule.dto.ScheduleDetailResponse;
import com.planit.schedule.dto.ScheduleStopDeleteResponse;
import com.planit.schedule.dto.ScheduleStopAddRequest;
import com.planit.schedule.repository.ScheduleDayRepository;
import com.planit.schedule.repository.ScheduleLegRepository;
import com.planit.schedule.repository.PlaceRepository;
import com.planit.schedule.repository.ScheduleRepository;
import com.planit.schedule.repository.ScheduleVisitRepository;
import com.planit.trip.service.TripMemberAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleServiceImplTest {

    private static final UUID USER_PUBLIC_ID = UUID.fromString(
            "01991f6e-7300-7b21-a3cc-1436db3df95e"
    );

    private UserRepository userRepository;
    private TripRepository tripRepository;
    private TripMemberRepository tripMemberRepository;
    private ScheduleRepository scheduleRepository;
    private PlaceRepository placeRepository;
    private ScheduleDayRepository dayRepository;
    private ScheduleVisitRepository visitRepository;
    private ScheduleLegRepository legRepository;
    private ScheduleServiceImpl service;
    private User user;
    private Trip trip;
    private TripMember host;
    private Schedule schedule;
    private ScheduleDay day;
    private List<ScheduleVisit> visits;
    private List<ScheduleLeg> legs;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tripRepository = mock(TripRepository.class);
        tripMemberRepository = mock(TripMemberRepository.class);
        scheduleRepository = mock(ScheduleRepository.class);
        placeRepository = mock(PlaceRepository.class);
        dayRepository = mock(ScheduleDayRepository.class);
        visitRepository = mock(ScheduleVisitRepository.class);
        legRepository = mock(ScheduleLegRepository.class);
        service = new ScheduleServiceImpl(
                userRepository,
                tripRepository,
                tripMemberRepository,
                new TripMemberAccessService(tripMemberRepository),
                placeRepository,
                scheduleRepository,
                dayRepository,
                visitRepository,
                legRepository
        );

        user = mock(User.class);
        trip = mock(Trip.class);
        when(trip.getStartDate()).thenReturn(LocalDate.now().plusDays(10));
        when(trip.getId()).thenReturn(1L);
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(tripRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(trip));
        when(userRepository.findByPublicIdAndDeletedAtIsNull(USER_PUBLIC_ID))
                .thenReturn(Optional.of(user));

        host = mock(TripMember.class);
        when(host.getId()).thenReturn(5L);
        when(host.getActiveSlot()).thenReturn((byte) 1);
        when(host.getHostSlot()).thenReturn((byte) 1);
        when(host.isActive()).thenReturn(true);
        when(host.isCurrentHost()).thenReturn(true);
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(trip, user))
                .thenReturn(Optional.of(host));

        schedule = new Schedule(trip, LocalDateTime.now());
        ReflectionTestUtils.setField(schedule, "id", 10L);
        day = new ScheduleDay(schedule, (byte) 1, LocalDate.now().plusDays(10));
        ReflectionTestUtils.setField(day, "id", 20L);
        when(scheduleRepository.findByTripIdAndActiveConfirmedSlot(1L, (byte) 1))
                .thenReturn(Optional.of(schedule));

        visits = List.of(
                visit(101L, 201L, 1, 35.0, 129.0),
                visit(102L, 202L, 2, 35.1, 129.1),
                visit(103L, 203L, 3, 35.2, 129.2),
                visit(104L, 204L, 4, 35.3, 129.3)
        );
        legs = List.of(
                leg(301L, visits.get(0), visits.get(1), 1),
                leg(302L, visits.get(1), visits.get(2), 2),
                leg(303L, visits.get(2), visits.get(3), 3)
        );
        when(legRepository.findByDayIdOrderByLegOrderAsc(20L))
                .thenReturn(legs, List.of());
        when(legRepository.save(any(ScheduleLeg.class)))
                .thenAnswer(invocation -> {
                    ScheduleLeg leg = invocation.getArgument(0);
                    ReflectionTestUtils.setField(leg, "id", 999L);
                    return leg;
                });
    }

    @DisplayName("여행 행을 잠그고 최신 후보를 확정한다")
    @Test
    void confirmsLatestScheduleCandidate() {
        Schedule selected = candidate(12L, ScheduleStrategy.PREFERENCE, 92);
        when(scheduleRepository.findByIdAndTripId(12L, 1L))
                .thenReturn(Optional.of(selected));
        when(scheduleRepository.findFirstByTripIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(selected));

        ScheduleConfirmResponse response = service.confirmSchedule(
                USER_PUBLIC_ID.toString(),
                1L,
                new ScheduleConfirmRequest(12L)
        );

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(selected.getActiveConfirmedSlot()).isEqualTo((byte) 1);
        assertThat(selected.getConfirmedByMember()).isSameAs(host);
        assertThat(selected.getConfirmedAt()).isNotNull();
        verify(tripRepository).findByIdForUpdate(1L);
        verify(scheduleRepository).flush();
    }

    @DisplayName("후보를 확정하면 고르지 않은 나머지 후보를 대체 처리한다")
    @Test
    void supersedesUnselectedCandidatesOnConfirm() {
        Schedule shortest = candidate(11L, ScheduleStrategy.SHORTEST, null);
        Schedule selected = candidate(12L, ScheduleStrategy.PREFERENCE, 92);
        Schedule balanced = candidate(13L, ScheduleStrategy.BALANCED, 68);
        when(scheduleRepository.findByIdAndTripId(12L, 1L))
                .thenReturn(Optional.of(selected));
        when(scheduleRepository.findFirstByTripIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(selected));
        when(scheduleRepository.findByTripIdAndStatus(1L, "DRAFT"))
                .thenReturn(List.of(shortest, balanced));

        service.confirmSchedule(
                USER_PUBLIC_ID.toString(),
                1L,
                new ScheduleConfirmRequest(12L)
        );

        assertThat(selected.getStatus()).isEqualTo("ACTIVE");
        assertThat(shortest.getStatus()).isEqualTo("SUPERSEDED");
        assertThat(balanced.getStatus()).isEqualTo("SUPERSEDED");
    }

    @DisplayName("같은 후보를 다시 확정해도 확정 정보가 변경되지 않는다")
    @Test
    void confirmsSameScheduleIdempotently() {
        Schedule selected = candidate(12L, ScheduleStrategy.PREFERENCE, 92);
        LocalDateTime firstConfirmedAt = LocalDateTime.now().minusMinutes(1);
        selected.confirm(host, firstConfirmedAt);
        when(scheduleRepository.findByIdAndTripId(12L, 1L))
                .thenReturn(Optional.of(selected));
        when(scheduleRepository.findFirstByTripIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(selected));

        service.confirmSchedule(
                USER_PUBLIC_ID.toString(),
                1L,
                new ScheduleConfirmRequest(12L)
        );

        assertThat(selected.getConfirmedAt()).isEqualTo(firstConfirmedAt);
        assertThat(selected.getConfirmedByMember()).isSameAs(host);
        verify(scheduleRepository, never()).flush();
    }

    @DisplayName("다른 후보를 확정하면 기존 확정 일정을 대체한다")
    @Test
    void supersedesActiveScheduleWhenConfirmingAnotherCandidate() {
        Schedule selected = candidate(12L, ScheduleStrategy.PREFERENCE, 92);
        when(scheduleRepository.findByIdAndTripId(12L, 1L))
                .thenReturn(Optional.of(selected));
        when(scheduleRepository.findFirstByTripIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(selected));
        when(scheduleRepository.findByTripIdAndActiveConfirmedSlot(1L, (byte) 1))
                .thenReturn(Optional.of(schedule));

        service.confirmSchedule(
                USER_PUBLIC_ID.toString(),
                1L,
                new ScheduleConfirmRequest(12L)
        );

        assertThat(schedule.getStatus()).isEqualTo("SUPERSEDED");
        assertThat(schedule.getActiveConfirmedSlot()).isNull();
        assertThat(selected.getStatus()).isEqualTo("ACTIVE");
    }

    @DisplayName("최신 후보가 아니면 확정을 거부한다")
    @Test
    void rejectsOutdatedScheduleCandidate() {
        Schedule selected = candidate(12L, ScheduleStrategy.PREFERENCE, 92);
        Schedule latest = candidate(13L, ScheduleStrategy.BALANCED, 68);
        ReflectionTestUtils.setField(
                selected,
                "createdAt",
                LocalDateTime.now().minusMinutes(1)
        );
        when(scheduleRepository.findByIdAndTripId(12L, 1L))
                .thenReturn(Optional.of(selected));
        when(scheduleRepository.findFirstByTripIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(latest));

        assertConfirmError(ErrorCode.SCHEDULE_CANDIDATES_SUPERSEDED);
        verify(scheduleRepository, never()).flush();
    }

    @DisplayName("방장이 아니면 후보를 확정할 수 없다")
    @Test
    void rejectsConfirmationByNonHost() {
        when(host.isCurrentHost()).thenReturn(false);

        assertConfirmError(ErrorCode.TRIP_HOST_REQUIRED);
        verify(scheduleRepository, never()).findByIdAndTripId(any(), any());
    }

    @DisplayName("여행 시작 전날 23시 50분 이후에는 후보 확정을 거부한다")
    @Test
    void rejectsConfirmationAfterDeadline() {
        when(trip.getStartDate()).thenReturn(LocalDate.now());

        assertConfirmError(ErrorCode.SCHEDULE_CHANGE_NOT_ALLOWED);
        verify(scheduleRepository, never()).findByIdAndTripId(any(), any());
    }

    @DisplayName("변경 기한 전 방장에게 확정 일정을 수정 가능 상태로 반환한다")
    @Test
    void returnsConfirmedScheduleEditableForHostBeforeDeadline() {
        when(dayRepository.findByScheduleIdOrderByDayNumberAsc(10L))
                .thenReturn(List.of(day));
        when(visitRepository.findByDayIdAndStatusOrderByVisitOrderAsc(
                20L,
                "ACTIVE"
        )).thenReturn(visits);

        ScheduleDetailResponse response = service.getSchedule(
                USER_PUBLIC_ID.toString(),
                1L
        );

        assertThat(response.editable()).isTrue();
        assertThat(response.confirmedAt())
                .isEqualTo(schedule.getConfirmedAt()
                        .atZone(java.time.ZoneId.of("Asia/Seoul"))
                        .toOffsetDateTime());
    }

    @DisplayName("활성 그룹원에게 세 후보를 전략 순서대로 반환한다")
    @Test
    void getsScheduleCandidatesForActiveMember() {
        TripMember member = mock(TripMember.class);
        when(member.getActiveSlot()).thenReturn((byte) 1);
        when(member.isCurrentHost()).thenReturn(false);
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(trip, user))
                .thenReturn(Optional.of(member));

        Schedule shortest = candidate(11L, ScheduleStrategy.SHORTEST, null);
        Schedule preference = candidate(12L, ScheduleStrategy.PREFERENCE, 92);
        Schedule balanced = candidate(13L, ScheduleStrategy.BALANCED, 68);
        when(scheduleRepository.findByTripIdAndStatus(1L, "DRAFT"))
                .thenReturn(List.of(balanced, shortest, preference));
        givenCandidateDay(shortest, 21L, 120);
        givenCandidateDay(preference, 22L, 240);
        givenCandidateDay(balanced, 23L, 180);

        ScheduleCandidateListResponse response =
                service.getScheduleCandidates(
                        USER_PUBLIC_ID.toString(),
                        1L
                );

        assertThat(response.candidates())
                .extracting(
                        ScheduleCandidateListResponse.Candidate::strategy,
                        ScheduleCandidateListResponse.Candidate::preferenceScore,
                        ScheduleCandidateListResponse.Candidate::totalDistanceMeters
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("SHORTEST", null, 120),
                        org.assertj.core.groups.Tuple.tuple("PREFERENCE", 92, 240),
                        org.assertj.core.groups.Tuple.tuple("BALANCED", 68, 180)
                );
    }

    @DisplayName("후보 상세에 날짜별 방문 순서와 이동 구간을 반환한다")
    @Test
    void getsScheduleCandidateDetail() {
        Schedule preference = candidate(12L, ScheduleStrategy.PREFERENCE, 92);
        ScheduleDay candidateDay = givenCandidateDay(preference, 22L, 240);
        when(scheduleRepository.findByIdAndTripIdAndStatus(12L, 1L, "DRAFT"))
                .thenReturn(Optional.of(preference));
        when(visitRepository.findByDayIdAndStatusOrderByVisitOrderAsc(
                22L,
                "ACTIVE"
        )).thenReturn(visits);

        ScheduleCandidateDetailResponse response =
                service.getScheduleCandidate(
                        USER_PUBLIC_ID.toString(),
                        1L,
                        12L
                );

        assertThat(response.scheduleId()).isEqualTo("12");
        assertThat(response.strategy()).isEqualTo("PREFERENCE");
        assertThat(response.preferenceScore()).isEqualTo(92);
        assertThat(response.editable()).isFalse();
        assertThat(response.totalDistanceMeters()).isEqualTo(240);
        assertThat(response.days()).singleElement()
                .satisfies(result -> {
                    assertThat(result.dayId())
                            .isEqualTo(candidateDay.getId().toString());
                    assertThat(result.stops()).hasSize(4);
                    assertThat(result.legs()).hasSize(1);
                });
    }

    @DisplayName("다른 여행방의 후보 상세 조회를 거부한다")
    @Test
    void rejectsCandidateFromAnotherTrip() {
        when(scheduleRepository.findByIdAndTripIdAndStatus(12L, 1L, "DRAFT"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getScheduleCandidate(
                USER_PUBLIC_ID.toString(),
                1L,
                12L
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SCHEDULE_CANDIDATE_NOT_FOUND)
        );
    }

    @DisplayName("후보가 없으면 후보 없음으로 응답한다")
    @Test
    void rejectsEmptyCandidateList() {
        when(scheduleRepository.findByTripIdAndStatus(1L, "DRAFT"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getScheduleCandidates(
                USER_PUBLIC_ID.toString(),
                1L
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SCHEDULE_CANDIDATES_NOT_FOUND)
        );
    }

    @DisplayName("후보 개수가 전략 수와 다르면 데이터 오류로 보고 목록을 공개하지 않는다")
    @Test
    void rejectsIncompleteCandidateList() {
        when(scheduleRepository.findByTripIdAndStatus(1L, "DRAFT"))
                .thenReturn(List.of(candidate(
                        11L,
                        ScheduleStrategy.SHORTEST,
                        null
                )));

        assertThatThrownBy(() -> service.getScheduleCandidates(
                USER_PUBLIC_ID.toString(),
                1L
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR)
        );
    }

    @DisplayName("활성 그룹원이 아니면 후보 목록을 조회할 수 없다")
    @Test
    void rejectsCandidateListFromNonMember() {
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(trip, user))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getScheduleCandidates(
                USER_PUBLIC_ID.toString(),
                1L
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.TRIP_MEMBER_REQUIRED)
        );
        verify(scheduleRepository, never()).findByTripIdAndStatus(any(), any());
    }

    @DisplayName("가운데 장소를 삭제하고 양옆 장소를 새 구간으로 연결한다")
    @Test
    void deletesMiddleStopAndReconnectsAdjacentStops() {
        ScheduleVisit target = visits.get(1);
        when(visitRepository.findWithDayAndScheduleById(102L))
                .thenReturn(Optional.of(target));
        when(visitRepository.findByDayIdAndStatusOrderByVisitOrderAsc(20L, "ACTIVE"))
                .thenReturn(visits, List.of(visits.get(0), visits.get(2), visits.get(3)));

        ScheduleStopDeleteResponse response = service.deleteStop(
                USER_PUBLIC_ID.toString(),
                1L,
                102L
        );

        assertThat(response.deletedStopId()).isEqualTo("102");
        assertThat(target.getStatus()).isEqualTo("REMOVED");
        assertThat(target.getRemovedAt()).isNotNull();
        assertThat(visits.get(2).getVisitOrder()).isEqualTo((short) 2);
        assertThat(visits.get(3).getVisitOrder()).isEqualTo((short) 3);
        assertThat(legs.get(2).getLegOrder()).isEqualTo((short) 2);

        verify(legRepository).deleteAll(List.of(legs.get(0), legs.get(1)));
        ArgumentCaptor<ScheduleLeg> bridgeCaptor =
                ArgumentCaptor.forClass(ScheduleLeg.class);
        verify(legRepository).save(bridgeCaptor.capture());
        ScheduleLeg bridge = bridgeCaptor.getValue();
        assertThat(bridge.getFromVisit()).isSameAs(visits.get(0));
        assertThat(bridge.getToVisit()).isSameAs(visits.get(2));
        assertThat(bridge.getLegOrder()).isEqualTo((short) 1);
        assertThat(bridge.getDistanceMeters()).isPositive();
    }

    @DisplayName("이미 삭제된 장소를 다시 삭제하면 현재 결과를 반환한다")
    @Test
    void returnsCurrentResultWhenStopIsAlreadyRemoved() {
        ScheduleVisit target = visits.get(1);
        target.remove(LocalDateTime.now().minusMinutes(1));
        when(visitRepository.findWithDayAndScheduleById(102L))
                .thenReturn(Optional.of(target));
        when(visitRepository.findByDayIdAndStatusOrderByVisitOrderAsc(20L, "ACTIVE"))
                .thenReturn(List.of(visits.get(0), visits.get(2), visits.get(3)));
        when(legRepository.findByDayIdOrderByLegOrderAsc(20L))
                .thenReturn(List.of());

        ScheduleStopDeleteResponse response = service.deleteStop(
                USER_PUBLIC_ID.toString(),
                1L,
                102L
        );

        assertThat(response.deletedStopId()).isEqualTo("102");
        verify(legRepository, never()).deleteAll(any());
    }

    @DisplayName("첫 장소를 삭제하면 첫 구간을 제거하고 이후 순서를 당긴다")
    @Test
    void deletesFirstStop() {
        when(visitRepository.findWithDayAndScheduleById(101L))
                .thenReturn(Optional.of(visits.get(0)));
        when(visitRepository.findByDayIdAndStatusOrderByVisitOrderAsc(20L, "ACTIVE"))
                .thenReturn(visits, visits.subList(1, visits.size()));

        service.deleteStop(USER_PUBLIC_ID.toString(), 1L, 101L);

        verify(legRepository).deleteAll(List.of(legs.get(0)));
        verify(legRepository, never()).save(any(ScheduleLeg.class));
        assertThat(visits.get(1).getVisitOrder()).isEqualTo((short) 1);
        assertThat(legs.get(1).getLegOrder()).isEqualTo((short) 1);
    }

    @DisplayName("마지막 장소를 삭제하면 마지막 구간만 제거한다")
    @Test
    void deletesLastStop() {
        when(visitRepository.findWithDayAndScheduleById(104L))
                .thenReturn(Optional.of(visits.get(3)));
        when(visitRepository.findByDayIdAndStatusOrderByVisitOrderAsc(20L, "ACTIVE"))
                .thenReturn(visits, visits.subList(0, 3));

        service.deleteStop(USER_PUBLIC_ID.toString(), 1L, 104L);

        verify(legRepository).deleteAll(List.of(legs.get(2)));
        verify(legRepository, never()).save(any(ScheduleLeg.class));
        assertThat(visits.get(2).getVisitOrder()).isEqualTo((short) 3);
    }

    @DisplayName("기존 장소를 가운데에 추가하고 기존 구간을 두 구간으로 교체한다")
    @Test
    void addsExistingPlaceBetweenStops() {
        Region region = mock(Region.class);
        when(region.getId()).thenReturn(30L);
        when(trip.getRegion()).thenReturn(region);
        when(dayRepository.findByIdAndSchedule_Id(20L, 10L))
                .thenReturn(Optional.of(day));
        when(visitRepository.findByDayIdAndStatusOrderByVisitOrderAsc(
                20L,
                "ACTIVE"
        )).thenReturn(visits, visits);

        Place place = new Place(
                region,
                new PlaceDetails(
                        "google-new",
                        "이전 장소명",
                        null,
                        null,
                        null,
                        BigDecimal.valueOf(129.15),
                        BigDecimal.valueOf(35.15),
                        null,
                        null
                ),
                LocalDateTime.now().minusDays(1)
        );
        ReflectionTestUtils.setField(place, "id", 999L);
        when(placeRepository.findByRegion_IdAndGooglePlaceId(
                30L,
                "google-new"
        )).thenReturn(Optional.of(place));
        when(visitRepository.save(any(ScheduleVisit.class)))
                .thenAnswer(invocation -> {
                    ScheduleVisit visit = invocation.getArgument(0);
                    ReflectionTestUtils.setField(visit, "id", 888L);
                    return visit;
                });

        service.addStop(
                USER_PUBLIC_ID.toString(),
                1L,
                addRequest(3)
        );

        assertThat(place.getName()).isEqualTo("새 장소");
        assertThat(place.getAddress()).isEqualTo("경주시 주소");
        assertThat(visits.get(2).getVisitOrder()).isEqualTo((short) 4);
        assertThat(visits.get(3).getVisitOrder()).isEqualTo((short) 5);
        assertThat(legs.get(2).getLegOrder()).isEqualTo((short) 4);
        verify(legRepository).delete(legs.get(1));

        ArgumentCaptor<ScheduleVisit> visitCaptor =
                ArgumentCaptor.forClass(ScheduleVisit.class);
        verify(visitRepository).save(visitCaptor.capture());
        ScheduleVisit added = visitCaptor.getValue();
        assertThat(added.getVisitOrder()).isEqualTo((short) 3);
        assertThat(added.getSource()).isEqualTo("MANUAL");
        assertThat(added.getSelectionReason())
                .isEqualTo("사용자가 직접 추가한 장소입니다.");

        ArgumentCaptor<ScheduleLeg> legCaptor =
                ArgumentCaptor.forClass(ScheduleLeg.class);
        verify(legRepository, org.mockito.Mockito.times(2))
                .save(legCaptor.capture());
        assertThat(legCaptor.getAllValues())
                .extracting(ScheduleLeg::getLegOrder)
                .containsExactly((short) 2, (short) 3);
    }

    @DisplayName("첫 위치에 장소를 추가하면 기존 장소와 구간 순서를 뒤로 민다")
    @Test
    void addsStopAtFirstPosition() {
        Place place = prepareExistingPlaceForAddition();
        when(visitRepository.save(any(ScheduleVisit.class)))
                .thenAnswer(invocation -> withId(
                        invocation.getArgument(0),
                        888L
                ));

        service.addStop(
                USER_PUBLIC_ID.toString(),
                1L,
                addRequest(1)
        );

        assertThat(visits)
                .extracting(ScheduleVisit::getVisitOrder)
                .containsExactly((short) 2, (short) 3, (short) 4, (short) 5);
        assertThat(legs)
                .extracting(ScheduleLeg::getLegOrder)
                .containsExactly((short) 2, (short) 3, (short) 4);
        verify(legRepository, never()).delete(any(ScheduleLeg.class));

        ArgumentCaptor<ScheduleLeg> legCaptor =
                ArgumentCaptor.forClass(ScheduleLeg.class);
        verify(legRepository).save(legCaptor.capture());
        assertThat(legCaptor.getValue().getLegOrder()).isEqualTo((short) 1);
        assertThat(legCaptor.getValue().getFromVisit().getPlace())
                .isSameAs(place);
        assertThat(legCaptor.getValue().getToVisit()).isSameAs(visits.getFirst());
    }

    @DisplayName("마지막 위치에 신규 장소를 저장하고 마지막 구간을 추가한다")
    @Test
    void addsNewPlaceAtLastPosition() {
        prepareAdditionContext();
        when(placeRepository.findByRegion_IdAndGooglePlaceId(
                30L,
                "google-new"
        )).thenReturn(Optional.empty());
        when(placeRepository.save(any(Place.class)))
                .thenAnswer(invocation -> withId(
                        invocation.getArgument(0),
                        999L
                ));
        when(visitRepository.save(any(ScheduleVisit.class)))
                .thenAnswer(invocation -> withId(
                        invocation.getArgument(0),
                        888L
                ));

        service.addStop(
                USER_PUBLIC_ID.toString(),
                1L,
                addRequest(5)
        );

        ArgumentCaptor<Place> placeCaptor = ArgumentCaptor.forClass(Place.class);
        verify(placeRepository).save(placeCaptor.capture());
        Place savedPlace = placeCaptor.getValue();
        assertThat(savedPlace.getGooglePlaceId()).isEqualTo("google-new");
        assertThat(savedPlace.getName()).isEqualTo("새 장소");
        assertThat(savedPlace.getAddress()).isEqualTo("경주시 주소");
        assertThat(savedPlace.getPlaceUrl())
                .isEqualTo("https://maps.example/place");
        ArgumentCaptor<ScheduleLeg> legCaptor =
                ArgumentCaptor.forClass(ScheduleLeg.class);
        verify(legRepository).save(legCaptor.capture());
        assertThat(legCaptor.getValue().getLegOrder()).isEqualTo((short) 4);
        assertThat(legCaptor.getValue().getFromVisit()).isSameAs(visits.getLast());
        assertThat(legCaptor.getValue().getToVisit().getPlace())
                .isSameAs(savedPlace);
    }

    @DisplayName("그날 장소가 열 개이면 추가할 수 없다")
    @Test
    void rejectsAdditionOverMaximumStopCount() {
        when(dayRepository.findByIdAndSchedule_Id(20L, 10L))
                .thenReturn(Optional.of(day));
        when(visitRepository.findByDayIdAndStatusOrderByVisitOrderAsc(
                20L,
                "ACTIVE"
        )).thenReturn(dayVisits(10));

        assertThatThrownBy(() -> service.addStop(
                USER_PUBLIC_ID.toString(),
                1L,
                addRequest(1)
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SCHEDULE_MAXIMUM_STOPS_EXCEEDED)
        );
        verify(placeRepository, never()).save(any(Place.class));
    }

    @DisplayName("같은 장소가 활성 일정에 있으면 중복 추가를 거부한다")
    @Test
    void rejectsDuplicatePlaceInSchedule() {
        Region region = mock(Region.class);
        when(region.getId()).thenReturn(30L);
        when(trip.getRegion()).thenReturn(region);
        when(dayRepository.findByIdAndSchedule_Id(20L, 10L))
                .thenReturn(Optional.of(day));
        when(visitRepository.findByDayIdAndStatusOrderByVisitOrderAsc(
                20L,
                "ACTIVE"
        )).thenReturn(visits);
        Place place = visits.getFirst().getPlace();
        when(placeRepository.findByRegion_IdAndGooglePlaceId(
                30L,
                "google-new"
        )).thenReturn(Optional.of(place));
        when(visitRepository.existsByDay_Schedule_IdAndPlace_IdAndStatus(
                10L,
                place.getId(),
                "ACTIVE"
        )).thenReturn(true);

        assertThatThrownBy(() -> service.addStop(
                USER_PUBLIC_ID.toString(),
                1L,
                addRequest(1)
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.SCHEDULE_PLACE_ALREADY_EXISTS)
        );
        verify(visitRepository, never()).save(any(ScheduleVisit.class));
    }

    @DisplayName("방장이 아니면 일정 장소를 삭제할 수 없다")
    @Test
    void rejectsNonHost() {
        TripMember member = mock(TripMember.class);
        when(member.getActiveSlot()).thenReturn((byte) 1);
        when(member.isActive()).thenReturn(true);
        when(member.isCurrentHost()).thenReturn(false);
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(trip, user))
                .thenReturn(Optional.of(member));

        assertError(ErrorCode.TRIP_HOST_REQUIRED);
    }

    @DisplayName("여행 시작일부터는 일정 장소를 삭제할 수 없다")
    @Test
    void rejectsDeletionOnOrAfterTripStart() {
        when(trip.getStartDate()).thenReturn(LocalDate.now());

        assertError(ErrorCode.SCHEDULE_CHANGE_NOT_ALLOWED);
    }

    @DisplayName("그날 장소가 세 개이면 더 삭제할 수 없다")
    @Test
    void preservesMinimumStopCount() {
        when(visitRepository.findWithDayAndScheduleById(102L))
                .thenReturn(Optional.of(visits.get(1)));
        when(visitRepository.findByDayIdAndStatusOrderByVisitOrderAsc(
                20L,
                "ACTIVE"
        )).thenReturn(visits.subList(0, 3));

        assertError(ErrorCode.SCHEDULE_MINIMUM_STOPS_REQUIRED);
        verify(legRepository, never()).deleteAll(any());
    }

    private List<ScheduleVisit> dayVisits(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(order -> visit(
                        500L + order,
                        600L + order,
                        order,
                        35.0 + order * 0.01,
                        129.0
                ))
                .toList();
    }

    private void assertError(ErrorCode errorCode) {
        assertThatThrownBy(() -> service.deleteStop(
                USER_PUBLIC_ID.toString(),
                1L,
                102L
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode)
        );
    }

    private void assertConfirmError(ErrorCode errorCode) {
        assertThatThrownBy(() -> service.confirmSchedule(
                USER_PUBLIC_ID.toString(),
                1L,
                new ScheduleConfirmRequest(12L)
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(errorCode)
        );
    }

    private ScheduleVisit visit(
            long visitId,
            long placeId,
            int order,
            double latitude,
            double longitude
    ) {
        Region region = mock(Region.class);
        Place place = new Place(
                region,
                new PlaceDetails(
                        "google-" + placeId,
                        "장소 " + placeId,
                        "관광명소",
                        null,
                        null,
                        BigDecimal.valueOf(longitude),
                        BigDecimal.valueOf(latitude),
                        null,
                        null
                ),
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(place, "id", placeId);
        ScheduleVisit visit = new ScheduleVisit(
                day,
                place,
                order,
                "추천 이유",
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(visit, "id", visitId);
        return visit;
    }

    private Schedule candidate(
            long id,
            ScheduleStrategy strategy,
            Integer preferenceScore
    ) {
        Schedule candidate = new Schedule(
                trip,
                strategy,
                preferenceScore,
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(candidate, "id", id);
        return candidate;
    }

    private ScheduleDay givenCandidateDay(
            Schedule candidate,
            long dayId,
            int distanceMeters
    ) {
        ScheduleDay candidateDay = new ScheduleDay(
                candidate,
                (byte) 1,
                LocalDate.now().plusDays(10)
        );
        ReflectionTestUtils.setField(candidateDay, "id", dayId);
        when(dayRepository.findByScheduleIdOrderByDayNumberAsc(candidate.getId()))
                .thenReturn(List.of(candidateDay));
        ScheduleLeg candidateLeg = new ScheduleLeg(
                candidateDay,
                visits.getFirst(),
                visits.get(1),
                1,
                distanceMeters
        );
        ReflectionTestUtils.setField(candidateLeg, "id", dayId + 100);
        when(legRepository.findByDayIdOrderByLegOrderAsc(dayId))
                .thenReturn(List.of(candidateLeg));
        when(visitRepository.findByDayIdAndStatusOrderByVisitOrderAsc(
                dayId,
                "ACTIVE"
        )).thenReturn(List.of());
        return candidateDay;
    }

    private ScheduleLeg leg(
            long id,
            ScheduleVisit from,
            ScheduleVisit to,
            int order
    ) {
        ScheduleLeg leg = new ScheduleLeg(day, from, to, order, 100);
        ReflectionTestUtils.setField(leg, "id", id);
        return leg;
    }

    private ScheduleStopAddRequest addRequest(int position) {
        return new ScheduleStopAddRequest(
                20L,
                position,
                new ScheduleStopAddRequest.Place(
                        "google-new",
                        " 새 장소 ",
                        "카페",
                        " 경주시 주소 ",
                        null,
                        BigDecimal.valueOf(129.15),
                        BigDecimal.valueOf(35.15),
                        "054-000-0000",
                        "https://maps.example/place"
                )
        );
    }

    private Place prepareExistingPlaceForAddition() {
        Region region = prepareAdditionContext();
        Place place = new Place(
                region,
                new PlaceDetails(
                        "google-new",
                        "기존 장소",
                        null,
                        null,
                        null,
                        BigDecimal.valueOf(129.15),
                        BigDecimal.valueOf(35.15),
                        null,
                        null
                ),
                LocalDateTime.now().minusDays(1)
        );
        ReflectionTestUtils.setField(place, "id", 999L);
        when(placeRepository.findByRegion_IdAndGooglePlaceId(
                30L,
                "google-new"
        )).thenReturn(Optional.of(place));
        return place;
    }

    private Region prepareAdditionContext() {
        Region region = mock(Region.class);
        when(region.getId()).thenReturn(30L);
        when(trip.getRegion()).thenReturn(region);
        when(dayRepository.findByIdAndSchedule_Id(20L, 10L))
                .thenReturn(Optional.of(day));
        when(visitRepository.findByDayIdAndStatusOrderByVisitOrderAsc(
                20L,
                "ACTIVE"
        )).thenReturn(visits, visits);
        return region;
    }

    private <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
