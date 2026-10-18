package com.planit.schedule.service;

import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.global.error.BusinessException;
import com.planit.repository.TripRepository;
import com.planit.schedule.ai.RecommendedPlace;
import com.planit.schedule.ai.RecommendedRoute;
import com.planit.schedule.domain.Place;
import com.planit.schedule.domain.Schedule;
import com.planit.schedule.domain.ScheduleDay;
import com.planit.schedule.domain.ScheduleLeg;
import com.planit.schedule.domain.ScheduleStrategy;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static com.planit.global.error.ErrorCode.INVALID_AI_PLACE_RESULT;
import static com.planit.global.error.ErrorCode.SCHEDULE_ALREADY_EXISTS;
import static com.planit.global.error.ErrorCode.SCHEDULE_REGENERATION_LIMIT_EXCEEDED;
import static com.planit.global.error.ErrorCode.TRIP_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        AtomicLong scheduleId = new AtomicLong(200);
        when(scheduleRepository.save(any(Schedule.class))).thenAnswer(invocation -> {
            Schedule schedule = invocation.getArgument(0);
            ReflectionTestUtils.setField(
                    schedule,
                    "id",
                    scheduleId.getAndIncrement()
            );
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

    @DisplayName("세 전략 후보를 확정되지 않은 일정으로 함께 저장한다")
    @Test
    void savesThreeDraftCandidates() {
        List<GeneratedScheduleResult> results = service.saveCandidates(
                1L,
                candidates(),
                ScheduleGenerationMode.INITIAL
        );

        assertThat(results)
                .extracting(GeneratedScheduleResult::scheduleId)
                .containsExactly("200", "201", "202");
        assertThat(results)
                .extracting(GeneratedScheduleResult::placeCount)
                .containsExactly(5, 5, 5);

        ArgumentCaptor<Schedule> scheduleCaptor =
                ArgumentCaptor.forClass(Schedule.class);
        verify(scheduleRepository, times(3)).save(scheduleCaptor.capture());
        assertThat(scheduleCaptor.getAllValues())
                .extracting(
                        Schedule::getStrategy,
                        Schedule::getStatus,
                        Schedule::getActiveConfirmedSlot,
                        Schedule::getPreferenceScore,
                        Schedule::getConfirmedAt
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "SHORTEST", "DRAFT", null, null, null
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "PREFERENCE", "DRAFT", null, 92, null
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                "BALANCED", "DRAFT", null, 68, null
                        )
                );
        verify(dayRepository, times(3)).save(any(ScheduleDay.class));
        verify(visitRepository, times(15)).save(any(ScheduleVisit.class));
        verify(legRepository, times(12)).save(any(ScheduleLeg.class));
    }

    @DisplayName("세 번째 후보 저장 실패를 전파해 전체 트랜잭션이 롤백되게 한다")
    @Test
    void propagatesCandidateFailureForTransactionRollback() {
        doThrow(new IllegalStateException("third candidate failed"))
                .when(dayRepository)
                .save(org.mockito.ArgumentMatchers.argThat(day ->
                        day.getSchedule().getStrategy().equals("BALANCED")
                ));

        assertThatThrownBy(() -> service.saveCandidates(
                1L,
                candidates(),
                ScheduleGenerationMode.INITIAL
        )).isInstanceOf(IllegalStateException.class);

        verify(scheduleRepository, times(3)).save(any(Schedule.class));
    }

    @DisplayName("후보 값이 유효하지 않으면 기존 일정을 변경하기 전에 거부한다")
    @Test
    void validatesCandidateValuesBeforeReplacingExistingSchedule() {
        givenExistingSchedule();
        List<RecommendedRoute> invalidCandidates = List.of(
                new RecommendedRoute(
                        ScheduleStrategy.SHORTEST,
                        100,
                        List.of(recommendations("shortest", 5))
                ),
                candidates().get(1),
                candidates().get(2)
        );

        assertThatThrownBy(() -> service.saveCandidates(
                1L,
                invalidCandidates,
                ScheduleGenerationMode.GENERAL_REGENERATION
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(INVALID_AI_PLACE_RESULT)
                );

        verify(scheduleRepository, never()).deleteAll(any());
        verify(trip, never()).increaseScheduleRegenerationCount();
    }

    @DisplayName("진행 중이던 AI 생성이 여행방 삭제 뒤 완료되면 후보를 반영하지 않는다")
    @Test
    void rejectsCandidatesForDeletedTrip() {
        when(trip.getDeletedAt()).thenReturn(
                LocalDateTime.of(2026, 9, 30, 23, 50)
        );

        assertThatThrownBy(() -> service.saveCandidates(
                1L,
                candidates(),
                ScheduleGenerationMode.INITIAL
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(TRIP_NOT_FOUND)
                );
        verify(tripRepository).findByIdForUpdate(1L);
        verify(scheduleRepository, never()).save(any(Schedule.class));
        verifyNoInteractions(
                placeRepository,
                dayRepository,
                visitRepository,
                legRepository
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

    @DisplayName("일반 재생성은 기존 일정을 삭제하고 새 일정으로 대체하며 재생성 횟수를 차감한다")
    @Test
    void replacesExistingScheduleAndCountsGeneralRegeneration() {
        givenExistingSchedule();
        when(trip.getScheduleRegenerationCount()).thenReturn(2);

        GeneratedScheduleResult result = service.save(
                1L,
                List.of(recommendations()),
                ScheduleGenerationMode.GENERAL_REGENERATION
        );

        assertThat(result.scheduleId()).isEqualTo("200");
        verify(scheduleRepository).findByTripId(1L);
        verify(scheduleRepository).deleteAll(any());
        verify(scheduleRepository).flush();
        verify(trip).increaseScheduleRegenerationCount();
    }

    @DisplayName("날씨 재생성은 기존 일정을 대체하지만 재생성 횟수를 차감하지 않는다")
    @Test
    void replacesExistingScheduleWithoutCountingWeatherRegeneration() {
        givenExistingSchedule();
        when(trip.getScheduleRegenerationCount()).thenReturn(5);

        service.save(
                1L,
                List.of(recommendations()),
                ScheduleGenerationMode.WEATHER_REGENERATION
        );

        verify(scheduleRepository).deleteAll(any());
        verify(trip, never()).increaseScheduleRegenerationCount();
    }

    @DisplayName("일반 재생성 횟수를 모두 사용했으면 기존 일정을 유지하고 거부한다")
    @Test
    void rejectsGeneralRegenerationOverLimit() {
        givenExistingSchedule();
        when(trip.getScheduleRegenerationCount()).thenReturn(5);

        assertThatThrownBy(() -> service.save(
                1L,
                List.of(recommendations()),
                ScheduleGenerationMode.GENERAL_REGENERATION
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(SCHEDULE_REGENERATION_LIMIT_EXCEEDED)
                );
        verify(scheduleRepository, never()).deleteAll(any());
        verify(trip, never()).increaseScheduleRegenerationCount();
    }

    private void givenExistingSchedule() {
        when(trip.getId()).thenReturn(1L);
        when(scheduleRepository.existsByTripIdAndActiveConfirmedSlot(
                1L,
                (byte) 1
        )).thenReturn(true);
        when(scheduleRepository.findByTripId(1L))
                .thenReturn(List.of(mock(Schedule.class)));
    }

    private List<RecommendedPlace> recommendations() {
        return recommendations(6);
    }

    private List<RecommendedRoute> candidates() {
        return List.of(
                new RecommendedRoute(
                        ScheduleStrategy.SHORTEST,
                        null,
                        List.of(recommendations("shortest", 5))
                ),
                new RecommendedRoute(
                        ScheduleStrategy.PREFERENCE,
                        92,
                        List.of(recommendations("preference", 5))
                ),
                new RecommendedRoute(
                        ScheduleStrategy.BALANCED,
                        68,
                        List.of(recommendations("balanced", 5))
                )
        );
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
