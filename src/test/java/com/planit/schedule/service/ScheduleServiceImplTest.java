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
import com.planit.schedule.domain.ScheduleVisit;
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
        when(tripRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(trip));
        when(userRepository.findByPublicIdAndDeletedAtIsNull(USER_PUBLIC_ID))
                .thenReturn(Optional.of(user));

        TripMember host = mock(TripMember.class);
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
        when(visitRepository.countByDay_Schedule_IdAndStatus(10L, "ACTIVE"))
                .thenReturn(4L);
        when(legRepository.findByDayIdOrderByLegOrderAsc(20L))
                .thenReturn(legs, List.of());
        when(legRepository.save(any(ScheduleLeg.class)))
                .thenAnswer(invocation -> {
                    ScheduleLeg leg = invocation.getArgument(0);
                    ReflectionTestUtils.setField(leg, "id", 999L);
                    return leg;
                });
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

    @DisplayName("전체 일정 장소가 열 개이면 추가할 수 없다")
    @Test
    void rejectsAdditionOverMaximumStopCount() {
        when(dayRepository.findByIdAndSchedule_Id(20L, 10L))
                .thenReturn(Optional.of(day));
        when(visitRepository.countByDay_Schedule_IdAndStatus(10L, "ACTIVE"))
                .thenReturn(10L);

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

    @DisplayName("전체 일정 장소가 세 개이면 더 삭제할 수 없다")
    @Test
    void preservesMinimumStopCount() {
        when(visitRepository.findWithDayAndScheduleById(102L))
                .thenReturn(Optional.of(visits.get(1)));
        when(visitRepository.countByDay_Schedule_IdAndStatus(10L, "ACTIVE"))
                .thenReturn(3L);

        assertError(ErrorCode.SCHEDULE_MINIMUM_STOPS_REQUIRED);
        verify(legRepository, never()).deleteAll(any());
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
