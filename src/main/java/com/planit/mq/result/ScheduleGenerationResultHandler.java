package com.planit.mq.result;

import com.planit.ai.AiPlaceSelectionResponse;
import com.planit.domain.Trip;
import com.planit.global.error.BusinessException;
import com.planit.mq.job.AiJobTerminationService;
import com.planit.mq.job.AiJobType;
import com.planit.schedule.ai.AiRecommendedPlaceMapper;
import com.planit.schedule.ai.RecommendedPlace;
import com.planit.schedule.ai.RecommendedRoute;
import com.planit.schedule.ai.ScheduleAiResultValidator;
import com.planit.schedule.domain.ScheduleGenerationJob;
import com.planit.schedule.domain.ScheduleGenerationJobStatus;
import com.planit.schedule.repository.ScheduleGenerationJobRepository;
import com.planit.schedule.route.RouteCalculationException;
import com.planit.schedule.service.PlacePersistenceService;
import com.planit.schedule.service.PlannedRoute;
import com.planit.schedule.service.ScheduleGenerationMode;
import com.planit.schedule.service.SchedulePersistenceService;
import com.planit.schedule.service.ScheduleRoutePlanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 일정 생성 결과를 반영한다. AI 응답 검증과 경로 계산은 트랜잭션 밖에서 먼저 끝내고,
 * 작업을 성공으로 바꾼 트랜잭션에서 일정 후보 3개를 저장한다. 성공으로 바꾸지 못했으면 이미 끝난 작업이므로 저장하지 않는다.
 * 다시 처리해도 같은 결과인 실패는 작업을 실패로 끝내고 알린다. 그 밖의 예외는 그대로 던져 다시 시도하게 한다.
 */
@Component
public class ScheduleGenerationResultHandler implements AiResultHandler {

    /** AI 응답을 읽을 수 없거나 일정 후보를 만들 수 없는 값인 경우의 실패 원인 */
    public static final String INVALID_AI_RESULT = "INVALID_AI_RESULT";

    private static final Logger log = LoggerFactory.getLogger(ScheduleGenerationResultHandler.class);

    private final ScheduleGenerationJobRepository jobRepository;
    private final AiJobTerminationService terminationService;
    private final ScheduleAiResultValidator resultValidator;
    private final AiRecommendedPlaceMapper placeMapper;
    private final ScheduleRoutePlanner routePlanner;
    private final PlacePersistenceService placePersistenceService;
    private final SchedulePersistenceService schedulePersistenceService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnlyTransaction;

    public ScheduleGenerationResultHandler(
            ScheduleGenerationJobRepository jobRepository,
            AiJobTerminationService terminationService,
            ScheduleAiResultValidator resultValidator,
            AiRecommendedPlaceMapper placeMapper,
            ScheduleRoutePlanner routePlanner,
            PlacePersistenceService placePersistenceService,
            SchedulePersistenceService schedulePersistenceService,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.jobRepository = jobRepository;
        this.terminationService = terminationService;
        this.resultValidator = resultValidator;
        this.placeMapper = placeMapper;
        this.routePlanner = routePlanner;
        this.placePersistenceService = placePersistenceService;
        this.schedulePersistenceService = schedulePersistenceService;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    @Override
    public AiJobType jobType() {
        return AiJobType.SCHEDULE_GENERATE;
    }

    @Override
    public void handle(AiResultEnvelope result, int retryCount) {
        Long jobId = result.jobId();
        if (!result.isSucceeded()) {
            fail(jobId, result.errorKind());
            return;
        }

        Optional<JobTarget> found = findQueuedTarget(jobId);
        if (found.isEmpty()) {
            log.info("이미 끝났거나 없는 일정 생성 작업의 결과라 반영하지 않음 jobId={}", jobId);
            return;
        }
        JobTarget target = found.get();
        if (target.tripDeleted()) {
            fail(jobId, AiJobTerminationService.TRIP_DELETED);
            return;
        }

        Optional<AiPlaceSelectionResponse> response = readResponse(result);
        if (response.isEmpty() || !resultValidator.isValid(response.get(), target.tripDayCount())) {
            fail(jobId, INVALID_AI_RESULT);
            return;
        }

        List<RecommendedRoute> routes;
        List<PlannedRoute> plannedRoutes;
        try {
            routes = placeMapper.mapRoutes(response.get());
            plannedRoutes = routePlanner.plan(routes);
        } catch (RouteCalculationException e) {
            fail(jobId, failureCodeOf(e));
            return;
        }

        Map<String, Long> placeIds = placePersistenceService.upsertAndFindIds(target.regionId(), placesOf(routes));
        try {
            transaction.executeWithoutResult(status -> {
                if (terminationService.succeedScheduleGeneration(jobId)) {
                    schedulePersistenceService.saveCandidates(target.tripId(), plannedRoutes, placeIds, target.mode());
                }
            });
        } catch (BusinessException e) {
            fail(jobId, failureCodeOf(e));
        }
    }

    /** 진행 중인 작업만 반영한다. 끝난 작업이면 AI 응답을 읽거나 경로를 계산하지 않는다. */
    private Optional<JobTarget> findQueuedTarget(Long jobId) {
        return readOnlyTransaction.execute(status -> jobRepository.findById(jobId)
                .filter(job -> job.getStatus() == ScheduleGenerationJobStatus.QUEUED)
                .map(JobTarget::of));
    }

    private Optional<AiPlaceSelectionResponse> readResponse(AiResultEnvelope result) {
        try {
            return Optional.ofNullable(objectMapper.treeToValue(result.result(), AiPlaceSelectionResponse.class));
        } catch (JacksonException e) {
            log.warn("일정 생성 결과 본문을 읽지 못함 jobId={}", result.jobId(), e);
            return Optional.empty();
        }
    }

    private void fail(Long jobId, String errorCode) {
        log.warn("일정 생성 작업 실패 처리 jobId={}, errorCode={}", jobId, errorCode);
        transaction.executeWithoutResult(status -> terminationService.failScheduleGeneration(jobId, errorCode));
    }

    private static String failureCodeOf(RouteCalculationException e) {
        return e.getReason() == RouteCalculationException.Reason.ROUTE_NOT_FOUND
                ? "SCHEDULE_ROUTE_NOT_FOUND"
                : INVALID_AI_RESULT;
    }

    /**
     * 결과를 저장하는 동안 난 업무 오류의 실패 원인. 그 사이 여행이 삭제됐으면 TRIP_DELETED,
     * 장소 값이 잘못됐으면 INVALID_AI_RESULT, 나머지는 오류 코드 이름 그대로 쓴다.
     */
    private static String failureCodeOf(BusinessException e) {
        return switch (e.getErrorCode()) {
            case TRIP_NOT_FOUND -> AiJobTerminationService.TRIP_DELETED;
            case INVALID_AI_PLACE_RESULT -> INVALID_AI_RESULT;
            default -> e.getErrorCode().name();
        };
    }

    private static List<RecommendedPlace> placesOf(List<RecommendedRoute> routes) {
        return routes.stream()
                .flatMap(route -> route.days().stream())
                .flatMap(List::stream)
                .toList();
    }

    /** 결과 반영에 필요한 작업과 여행 값 */
    private record JobTarget(
            long tripId,
            long regionId,
            long tripDayCount,
            ScheduleGenerationMode mode,
            boolean tripDeleted
    ) {

        private static JobTarget of(ScheduleGenerationJob job) {
            Trip trip = job.getTrip();
            return new JobTarget(
                    trip.getId(),
                    trip.getRegion().getId(),
                    ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1,
                    job.getJobType(),
                    trip.getDeletedAt() != null
            );
        }
    }
}
