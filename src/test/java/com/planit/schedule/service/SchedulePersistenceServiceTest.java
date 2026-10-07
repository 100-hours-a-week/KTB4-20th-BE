package com.planit.schedule.service;

import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.global.error.BusinessException;
import com.planit.repository.TripRepository;
import com.planit.schedule.ai.RecommendedPlace;
import com.planit.schedule.domain.Place;
import com.planit.schedule.domain.Schedule;
import com.planit.schedule.domain.ScheduleDay;
import com.planit.schedule.domain.ScheduleLeg;
import com.planit.schedule.domain.ScheduleVisit;
import com.planit.schedule.repository.PlaceRepository;
import com.planit.schedule.repository.ScheduleDayRepository;
import com.planit.schedule.repository.ScheduleLegRepository;
import com.planit.schedule.repository.ScheduleRepository;
import com.planit.schedule.repository.ScheduleVisitRepository;
import com.planit.schedule.route.PlaceCategoryGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static com.planit.global.error.ErrorCode.SCHEDULE_ALREADY_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SchedulePersistenceServiceTest {
    private TripRepository tripRepository;
    private PlaceRepository placeRepository;
    private ScheduleRepository scheduleRepository;
    private ScheduleDayRepository dayRepository;
    private ScheduleVisitRepository visitRepository;
    private ScheduleLegRepository legRepository;
    private SchedulePersistenceService service;
    private Trip trip;

    @BeforeEach
    void setUp() {
        tripRepository = mock(TripRepository.class);
        placeRepository = mock(PlaceRepository.class);
        scheduleRepository = mock(ScheduleRepository.class);
        dayRepository = mock(ScheduleDayRepository.class);
        visitRepository = mock(ScheduleVisitRepository.class);
        legRepository = mock(ScheduleLegRepository.class);
        service = new SchedulePersistenceService(
                tripRepository,
                placeRepository,
                scheduleRepository,
                dayRepository,
                visitRepository,
                legRepository
        );
        trip = mock(Trip.class);
        Region region = mock(Region.class);
        when(region.getId()).thenReturn(10L);
        when(trip.getRegion()).thenReturn(region);
        when(trip.getStartDate()).thenReturn(LocalDate.of(2026, 10, 1));
        when(trip.getEndDate()).thenReturn(LocalDate.of(2026, 10, 1));
        when(tripRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(trip));

        AtomicLong placeId = new AtomicLong(100);
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> {
            Place place = invocation.getArgument(0);
            ReflectionTestUtils.setField(place, "id", placeId.getAndIncrement());
            return place;
        });
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> {
            Schedule schedule = invocation.getArgument(0);
            ReflectionTestUtils.setField(schedule, "id", 200L);
            return schedule;
        });
        AtomicLong dayId = new AtomicLong(300);
        when(dayRepository.save(any(ScheduleDay.class))).thenAnswer(invocation -> {
            ScheduleDay day = invocation.getArgument(0);
            ReflectionTestUtils.setField(day, "id", dayId.getAndIncrement());
            return day;
        });
        AtomicLong visitId = new AtomicLong(400);
        when(visitRepository.save(any(ScheduleVisit.class))).thenAnswer(invocation -> {
            ScheduleVisit visit = invocation.getArgument(0);
            ReflectionTestUtils.setField(visit, "id", visitId.getAndIncrement());
            return visit;
        });
        when(placeRepository.getReferenceById(anyLong())).thenAnswer(invocation ->
                mock(Place.class)
        );
    }

    @DisplayName("하루 일정에 여섯 방문 장소와 다섯 이동 구간을 저장한다")
    @Test
    void savesOneDaySixVisitsAndFiveLegs() {
        GeneratedScheduleResult result = service.save(
                1L,
                List.of(recommendations())
        );

        assertThat(result.scheduleId()).isEqualTo("200");
        assertThat(result.dayIds()).containsExactly("300");
        assertThat(result.placeCount()).isEqualTo(6);
        assertThat(result.legCount()).isEqualTo(5);
        verify(placeRepository, times(6)).save(any(Place.class));
        verify(visitRepository, times(6)).save(any(ScheduleVisit.class));
        verify(legRepository, times(5)).save(any(ScheduleLeg.class));
    }

    @DisplayName("AI가 다섯 장소만 반환하면 다섯 방문 장소와 네 이동 구간을 저장한다")
    @Test
    void savesOneDayFiveVisitsAndFourLegsWhenAiFallsShortOfSix() {
        GeneratedScheduleResult result = service.save(
                1L,
                List.of(recommendations(5))
        );

        assertThat(result.placeCount()).isEqualTo(5);
        assertThat(result.legCount()).isEqualTo(4);
        verify(placeRepository, times(5)).save(any(Place.class));
        verify(visitRepository, times(5)).save(any(ScheduleVisit.class));
        verify(legRepository, times(4)).save(any(ScheduleLeg.class));
    }

    @DisplayName("여행 날짜마다 독립된 일정 일차와 동선을 저장한다")
    @Test
    void savesRouteForEachTripDay() {
        when(trip.getEndDate()).thenReturn(LocalDate.of(2026, 10, 2));

        GeneratedScheduleResult result = service.save(
                1L,
                List.of(
                        recommendations("day-1", 5),
                        recommendations("day-2", 5)
                )
        );

        assertThat(result.dayIds()).containsExactly("300", "301");
        assertThat(result.placeCount()).isEqualTo(10);
        assertThat(result.legCount()).isEqualTo(8);
        verify(visitRepository, times(10)).save(any(ScheduleVisit.class));
        verify(legRepository, times(8)).save(any(ScheduleLeg.class));

        ArgumentCaptor<ScheduleDay> dayCaptor =
                ArgumentCaptor.forClass(ScheduleDay.class);
        verify(dayRepository, times(2)).save(dayCaptor.capture());
        assertThat(dayCaptor.getAllValues())
                .extracting(
                        ScheduleDay::getDayNumber,
                        ScheduleDay::getScheduleDate
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                (byte) 1,
                                LocalDate.of(2026, 10, 1)
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                (byte) 2,
                                LocalDate.of(2026, 10, 2)
                        )
                );
    }

    @DisplayName("이미 활성 일정이 있는 여행의 일정 저장을 거부한다")
    @Test
    void rejectsTripThatAlreadyHasActiveSchedule() {
        when(scheduleRepository.existsByTripIdAndActiveConfirmedSlot(
                1L,
                (byte) 1
        )).thenReturn(true);

        assertThatThrownBy(() -> service.save(
                1L,
                List.of(recommendations())
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(SCHEDULE_ALREADY_EXISTS)
                );
        verifyNoInteractions(
                placeRepository,
                dayRepository,
                visitRepository,
                legRepository
        );
    }

    private List<RecommendedPlace> recommendations() {
        return recommendations(6);
    }

    private List<RecommendedPlace> recommendations(int count) {
        return recommendations("google", count);
    }

    private List<RecommendedPlace> recommendations(
            String prefix,
            int count
    ) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> new RecommendedPlace(
                        prefix + "-" + index,
                        "장소 " + index,
                        35.0,
                        129.0 + index * 0.01,
                        "관광명소",
                        index % 2 == 0
                                ? PlaceCategoryGroup.ACTIVITY
                                : PlaceCategoryGroup.TOURISM_CULTURE,
                        null,
                        List.of(),
                        List.of("HISTORY_CULTURE")
                ))
                .toList();
    }
}
