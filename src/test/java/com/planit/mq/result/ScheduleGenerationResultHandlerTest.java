package com.planit.mq.result;

import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.mq.job.AiJobTerminationService;
import com.planit.mq.job.AiJobType;
import com.planit.schedule.ai.AiRecommendedPlaceMapper;
import com.planit.schedule.ai.RecommendedPlace;
import com.planit.schedule.ai.RecommendedRoute;
import com.planit.schedule.ai.ScheduleAiResultValidator;
import com.planit.schedule.domain.ScheduleGenerationJob;
import com.planit.schedule.domain.ScheduleGenerationJobStatus;
import com.planit.schedule.domain.ScheduleStrategy;
import com.planit.schedule.repository.ScheduleGenerationJobRepository;
import com.planit.schedule.route.RouteCalculationException;
import com.planit.schedule.service.PlacePersistenceService;
import com.planit.schedule.service.PlannedRoute;
import com.planit.schedule.service.ScheduleGenerationMode;
import com.planit.schedule.service.SchedulePersistenceService;
import com.planit.schedule.service.ScheduleRoutePlanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ScheduleGenerationResultHandlerTest {

    private static final long JOB_ID = 42L;
    private static final long TRIP_ID = 7L;
    private static final long REGION_ID = 3L;
    private static final String AI_RESULT = """
            {"status_code":200,"data":{"places":[],"routes":[]}}
            """;

    private final ScheduleGenerationJobRepository jobRepository = mock(ScheduleGenerationJobRepository.class);
    private final AiJobTerminationService terminationService = mock(AiJobTerminationService.class);
    private final ScheduleAiResultValidator resultValidator = mock(ScheduleAiResultValidator.class);
    private final AiRecommendedPlaceMapper placeMapper = mock(AiRecommendedPlaceMapper.class);
    private final ScheduleRoutePlanner routePlanner = mock(ScheduleRoutePlanner.class);
    private final PlacePersistenceService placePersistenceService = mock(PlacePersistenceService.class);
    private final SchedulePersistenceService schedulePersistenceService = mock(SchedulePersistenceService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final ScheduleGenerationResultHandler handler = new ScheduleGenerationResultHandler(
            jobRepository,
            terminationService,
            resultValidator,
            placeMapper,
            routePlanner,
            placePersistenceService,
            schedulePersistenceService,
            jsonMapper,
            transactionManager
    );

    private final Trip trip = mock(Trip.class);
    private final RecommendedPlace place = mock(RecommendedPlace.class);
    private final List<RecommendedRoute> routes = List.of(
            new RecommendedRoute(ScheduleStrategy.SHORTEST, null, null, List.of(List.of(place)))
    );
    private final List<PlannedRoute> plannedRoutes = List.of(mock(PlannedRoute.class));
    private final Map<String, Long> placeIds = Map.of("google-1", 100L);

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());

        Region region = mock(Region.class);
        when(region.getId()).thenReturn(REGION_ID);
        when(trip.getId()).thenReturn(TRIP_ID);
        when(trip.getRegion()).thenReturn(region);
        when(trip.getStartDate()).thenReturn(LocalDate.of(2026, 12, 1));
        when(trip.getEndDate()).thenReturn(LocalDate.of(2026, 12, 2));
        givenJob(ScheduleGenerationJobStatus.QUEUED);

        when(resultValidator.isValid(any(), anyLong())).thenReturn(true);
        when(placeMapper.mapRoutes(any())).thenReturn(routes);
        when(routePlanner.plan(routes)).thenReturn(plannedRoutes);
        when(placePersistenceService.upsertAndFindIds(REGION_ID, List.of(place))).thenReturn(placeIds);
        when(terminationService.succeedScheduleGeneration(JOB_ID)).thenReturn(true);
    }

    @DisplayName("성공 결과를 검증하고 경로를 계산한 뒤, 작업을 성공으로 바꾼 트랜잭션에서 요청 방식대로 일정 후보를 저장한다")
    @Test
    void savesCandidatesAfterSucceeded() {
        handler.handle(succeeded(), 0);

        verify(resultValidator).isValid(any(), eq(2L));
        var order = inOrder(terminationService, schedulePersistenceService, transactionManager);
        order.verify(terminationService).succeedScheduleGeneration(JOB_ID);
        order.verify(schedulePersistenceService)
                .saveCandidates(TRIP_ID, plannedRoutes, placeIds, ScheduleGenerationMode.GENERAL_REGENERATION);
        order.verify(transactionManager).commit(any());
    }

    @DisplayName("작업을 성공으로 바꾸지 못했으면 이미 끝난 작업이므로 일정 후보를 저장하지 않는다")
    @Test
    void skipsSavingWhenAlreadyFinished() {
        when(terminationService.succeedScheduleGeneration(JOB_ID)).thenReturn(false);

        handler.handle(succeeded(), 0);

        verify(schedulePersistenceService, never()).saveCandidates(anyLong(), any(), any(), any());
    }

    @DisplayName("진행 중이 아닌 작업의 결과는 AI 응답을 읽지 않고 무시한다")
    @Test
    void ignoresFinishedJob() {
        givenJob(ScheduleGenerationJobStatus.FAILED);

        handler.handle(succeeded(), 0);

        verifyNoInteractions(resultValidator, placeMapper, terminationService);
    }

    @DisplayName("AI 처리 실패 결과는 AI가 준 실패 원인으로 작업을 끝낸다")
    @Test
    void failsWithAiErrorKind() {
        handler.handle(new AiResultEnvelope(1, JOB_ID, AiJobType.SCHEDULE_GENERATE, "m-1",
                AiResultStatus.FAILED, "AI_UNAVAILABLE", null), 2);

        verify(terminationService).failScheduleGeneration(JOB_ID, "AI_UNAVAILABLE");
        verifyNoInteractions(jobRepository);
    }

    @DisplayName("여행이 삭제됐으면 결과를 계산하지 않고 TRIP_DELETED로 끝낸다")
    @Test
    void failsWhenTripDeleted() {
        when(trip.getDeletedAt()).thenReturn(LocalDateTime.of(2026, 11, 11, 9, 0));

        handler.handle(succeeded(), 0);

        verify(terminationService).failScheduleGeneration(JOB_ID, AiJobTerminationService.TRIP_DELETED);
        verifyNoInteractions(resultValidator, placeMapper);
    }

    @DisplayName("일정 후보를 만들 수 없는 AI 응답은 INVALID_AI_RESULT로 끝낸다")
    @Test
    void failsWhenResultInvalid() {
        when(resultValidator.isValid(any(), anyLong())).thenReturn(false);

        handler.handle(succeeded(), 0);

        verify(terminationService).failScheduleGeneration(JOB_ID, ScheduleGenerationResultHandler.INVALID_AI_RESULT);
        verifyNoInteractions(placeMapper, placePersistenceService);
    }

    @DisplayName("경로 계산 실패는 동선이 없으면 SCHEDULE_ROUTE_NOT_FOUND, 장소 값이 잘못됐으면 INVALID_AI_RESULT로 끝낸다")
    @ParameterizedTest
    @CsvSource({
            "ROUTE_NOT_FOUND, SCHEDULE_ROUTE_NOT_FOUND",
            "INVALID_PLACE_RESULT, INVALID_AI_RESULT"
    })
    void failsWhenRouteCalculationFails(RouteCalculationException.Reason reason, String errorCode) {
        when(routePlanner.plan(routes)).thenThrow(new RouteCalculationException(reason, "경로 없음"));

        handler.handle(succeeded(), 0);

        verify(terminationService).failScheduleGeneration(JOB_ID, errorCode);
        verifyNoInteractions(placePersistenceService);
    }

    @DisplayName("저장 중 업무 오류가 나면 롤백하고 새 트랜잭션에서 오류 코드 이름으로 작업을 실패로 끝낸다")
    @ParameterizedTest
    @CsvSource({
            "SCHEDULE_REGENERATION_LIMIT_EXCEEDED, SCHEDULE_REGENERATION_LIMIT_EXCEEDED",
            "SCHEDULE_CANDIDATES_PENDING, SCHEDULE_CANDIDATES_PENDING",
            "TRIP_NOT_FOUND, TRIP_DELETED",
            "INVALID_AI_PLACE_RESULT, INVALID_AI_RESULT"
    })
    void failsAfterRollbackOnBusinessError(ErrorCode thrown, String errorCode) {
        doThrow(new BusinessException(thrown))
                .when(schedulePersistenceService).saveCandidates(anyLong(), any(), any(), any());

        handler.handle(succeeded(), 0);

        var order = inOrder(transactionManager, terminationService);
        order.verify(transactionManager).rollback(any());
        order.verify(terminationService).failScheduleGeneration(JOB_ID, errorCode);
        order.verify(transactionManager).commit(any());
    }

    @DisplayName("업무 오류가 아닌 예외는 실패로 끝내지 않고 그대로 던져 다시 시도하게 한다")
    @Test
    void rethrowsUnexpectedError() {
        doThrow(new QueryTimeoutException("timeout"))
                .when(schedulePersistenceService).saveCandidates(anyLong(), any(), any(), any());

        assertThatThrownBy(() -> handler.handle(succeeded(), 0)).isInstanceOf(QueryTimeoutException.class);
        verify(terminationService, never()).failScheduleGeneration(anyLong(), anyString());
    }

    private void givenJob(ScheduleGenerationJobStatus status) {
        ScheduleGenerationJob job = mock(ScheduleGenerationJob.class);
        when(job.getStatus()).thenReturn(status);
        when(job.getTrip()).thenReturn(trip);
        when(job.getJobType()).thenReturn(ScheduleGenerationMode.GENERAL_REGENERATION);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
    }

    private AiResultEnvelope succeeded() {
        return new AiResultEnvelope(1, JOB_ID, AiJobType.SCHEDULE_GENERATE, "m-1",
                AiResultStatus.SUCCEEDED, null, jsonMapper.readTree(AI_RESULT));
    }
}
