package com.planit.trip.service;

import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.schedule.repository.ScheduleRepository;
import com.planit.schedule.service.SchedulePersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UnconfirmedTripDeletionServiceTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 10, 19);
    private static final LocalDateTime DELETED_AT =
            LocalDateTime.of(2026, 10, 18, 23, 50);

    private TripRepository tripRepository;
    private TripMemberRepository tripMemberRepository;
    private ScheduleRepository scheduleRepository;
    private SchedulePersistenceService schedulePersistenceService;
    private UnconfirmedTripDeletionService service;

    @BeforeEach
    void setUp() {
        tripRepository = mock(TripRepository.class);
        tripMemberRepository = mock(TripMemberRepository.class);
        scheduleRepository = mock(ScheduleRepository.class);
        schedulePersistenceService = mock(SchedulePersistenceService.class);
        service = new UnconfirmedTripDeletionService(
                tripRepository,
                tripMemberRepository,
                scheduleRepository,
                new TripRoomDeleter(schedulePersistenceService)
        );
    }

    @DisplayName("다음 날 시작하는 여행 중 ACTIVE 확정 일정이 없는 여행만 삭제 대상으로 고른다")
    @Test
    void findsTripsWithoutActiveSchedule() {
        when(tripRepository.findActiveTripIdsStartingOn(START_DATE))
                .thenReturn(List.of(1L, 2L, 3L));
        when(tripRepository.findIdsWithActiveConfirmedSchedule(
                List.of(1L, 2L, 3L)
        )).thenReturn(List.of(2L));

        assertThat(service.findTargetTripIds(START_DATE))
                .containsExactly(1L, 3L);
    }

    @DisplayName("다음 날 시작하는 여행이 없으면 확정 일정을 조회하지 않는다")
    @Test
    void skipsConfirmedLookupWhenNoTripStarts() {
        when(tripRepository.findActiveTripIdsStartingOn(START_DATE))
                .thenReturn(List.of());

        assertThat(service.findTargetTripIds(START_DATE)).isEmpty();
        verify(tripRepository, never())
                .findIdsWithActiveConfirmedSchedule(anyList());
    }

    @DisplayName("확정 일정이 없으면 여행방을 삭제하고 모든 활성 멤버십과 일정을 정리한다")
    @Test
    void deletesUnconfirmedTrip() {
        Trip trip = trip(10L, START_DATE);
        TripMember host = TripMember.createHost(trip, null);
        TripMember member = TripMember.createMember(trip, null);
        when(tripMemberRepository.findActiveMembersByTripIncludingWithdrawn(
                trip
        )).thenReturn(List.of(host, member));

        boolean deleted = service.deleteIfUnconfirmed(
                10L,
                START_DATE,
                DELETED_AT
        );

        assertThat(deleted).isTrue();
        assertThat(trip.getDeletedAt()).isEqualTo(DELETED_AT);
        assertThat(List.of(host, member)).allSatisfy(value -> {
            assertThat(value.getLeftAt()).isEqualTo(DELETED_AT);
            assertThat(value.getActiveSlot()).isZero();
            assertThat(value.getHostSlot()).isNull();
        });
        verify(schedulePersistenceService).deleteAllForTrip(10L);
    }

    @DisplayName("잠금 후 다시 확인했을 때 확정 일정이 생겼으면 삭제하지 않는다")
    @Test
    void keepsTripConfirmedBeforeLock() {
        Trip trip = trip(10L, START_DATE);
        when(scheduleRepository.existsByTripIdAndActiveConfirmedSlot(
                10L,
                (byte) 1
        )).thenReturn(true);

        boolean deleted = service.deleteIfUnconfirmed(
                10L,
                START_DATE,
                DELETED_AT
        );

        assertThat(deleted).isFalse();
        assertThat(trip.getDeletedAt()).isNull();
        verify(schedulePersistenceService, never()).deleteAllForTrip(any());
    }

    @DisplayName("이미 삭제된 여행은 다시 삭제하지 않아 반복 실행해도 삭제 시각이 바뀌지 않는다")
    @Test
    void skipsAlreadyDeletedTrip() {
        Trip trip = trip(10L, START_DATE);
        LocalDateTime firstDeletedAt = DELETED_AT.minusMinutes(1);
        trip.delete(firstDeletedAt);

        boolean deleted = service.deleteIfUnconfirmed(
                10L,
                START_DATE,
                DELETED_AT
        );

        assertThat(deleted).isFalse();
        assertThat(trip.getDeletedAt()).isEqualTo(firstDeletedAt);
        verify(tripMemberRepository, never())
                .findActiveMembersByTripIncludingWithdrawn(any());
        verify(schedulePersistenceService, never()).deleteAllForTrip(any());
    }

    @DisplayName("잠금 후 확인한 시작일이 대상 날짜와 다르면 삭제하지 않는다")
    @Test
    void skipsTripStartingOnAnotherDate() {
        Trip trip = trip(10L, START_DATE.plusDays(1));

        assertThat(service.deleteIfUnconfirmed(
                10L,
                START_DATE,
                DELETED_AT
        )).isFalse();
        assertThat(trip.getDeletedAt()).isNull();
    }

    @DisplayName("여행이 존재하지 않으면 삭제하지 않는다")
    @Test
    void skipsMissingTrip() {
        when(tripRepository.findByIdForUpdate(10L))
                .thenReturn(Optional.empty());

        assertThat(service.deleteIfUnconfirmed(
                10L,
                START_DATE,
                DELETED_AT
        )).isFalse();
        verify(schedulePersistenceService, never()).deleteAllForTrip(any());
    }

    private Trip trip(Long id, LocalDate startDate) {
        Trip trip = new Trip(
                null,
                "제주 여행",
                startDate,
                startDate.plusDays(1),
                (byte) 4,
                startDate.minusDays(2).atStartOfDay()
        );
        ReflectionTestUtils.setField(
                trip,
                "id",
                id
        );
        when(tripRepository.findByIdForUpdate(id))
                .thenReturn(Optional.of(trip));
        return trip;
    }
}
