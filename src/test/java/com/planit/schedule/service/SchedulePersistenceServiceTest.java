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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        when(dayRepository.save(any(ScheduleDay.class))).thenAnswer(invocation -> {
            ScheduleDay day = invocation.getArgument(0);
            ReflectionTestUtils.setField(day, "id", 300L);
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

    @Test
    void savesOneDaySixVisitsAndFiveLegs() {
        GeneratedScheduleResult result = service.save(1L, recommendations());

        assertThat(result.scheduleId()).isEqualTo("200");
        assertThat(result.dayId()).isEqualTo("300");
        assertThat(result.placeCount()).isEqualTo(6);
        assertThat(result.legCount()).isEqualTo(5);
        verify(placeRepository, times(6)).save(any(Place.class));
        verify(visitRepository, times(6)).save(any(ScheduleVisit.class));
        verify(legRepository, times(5)).save(any(ScheduleLeg.class));
    }

    @Test
    void savesOneDayFiveVisitsAndFourLegsWhenAiFallsShortOfSix() {
        GeneratedScheduleResult result = service.save(1L, recommendations(5));

        assertThat(result.placeCount()).isEqualTo(5);
        assertThat(result.legCount()).isEqualTo(4);
        verify(placeRepository, times(5)).save(any(Place.class));
        verify(visitRepository, times(5)).save(any(ScheduleVisit.class));
        verify(legRepository, times(4)).save(any(ScheduleLeg.class));
    }

    @Test
    void rejectsTripThatAlreadyHasActiveSchedule() {
        when(scheduleRepository.existsByTripIdAndActiveConfirmedSlot(
                1L,
                (byte) 1
        )).thenReturn(true);

        assertThatThrownBy(() -> service.save(1L, recommendations()))
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
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> new RecommendedPlace(
                        "google-" + index,
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
