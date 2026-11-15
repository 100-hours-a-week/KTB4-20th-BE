package com.planit.mq.result;

import com.planit.mq.job.AiJobTerminationService;
import com.planit.mq.job.AiJobType;
import com.planit.photomission.ai.PhotoMissionGenerateResponse;
import com.planit.photomission.domain.MissionGenerationStatus;
import com.planit.photomission.repository.MissionGenerationJobRepository;
import com.planit.photomission.service.PhotoMissionGenerationRejectedException;
import com.planit.photomission.service.PhotoMissionGenerationTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 포토미션 생성 결과를 반영한다. 작업을 성공으로 바꾼 트랜잭션에서 방문 장소별 미션과 참여 상태를 저장하고,
 * 성공으로 바꾸지 못했으면 이미 끝난 작업이므로 저장하지 않는다. 포토미션 생성은 알림이 없다.
 * AI 결과가 일정 장소와 맞지 않거나 확정 일정이 없으면 롤백한 뒤 작업을 실패로 끝낸다.
 * 그 밖의 예외는 그대로 던져 다시 시도하게 한다.
 */
@Component
public class MissionGenerationResultHandler implements AiResultHandler {

    private static final Logger log = LoggerFactory.getLogger(MissionGenerationResultHandler.class);

    private final MissionGenerationJobRepository jobRepository;
    private final AiJobTerminationService terminationService;
    private final PhotoMissionGenerationTransactionService generationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transaction;
    private final TransactionTemplate readOnlyTransaction;

    public MissionGenerationResultHandler(
            MissionGenerationJobRepository jobRepository,
            AiJobTerminationService terminationService,
            PhotoMissionGenerationTransactionService generationService,
            ObjectMapper objectMapper,
            @Qualifier("photoMissionClock") Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.jobRepository = jobRepository;
        this.terminationService = terminationService;
        this.generationService = generationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction = new TransactionTemplate(transactionManager);
        this.readOnlyTransaction.setReadOnly(true);
    }

    @Override
    public AiJobType jobType() {
        return AiJobType.PHOTO_MISSION_GENERATE;
    }

    @Override
    public void handle(AiResultEnvelope result, int retryCount) {
        Long jobId = result.jobId();
        if (!result.isSucceeded()) {
            fail(jobId, result.errorKind());
            return;
        }

        Optional<Long> tripId = findQueuedTripId(jobId);
        if (tripId.isEmpty()) {
            log.info("이미 끝났거나 없는 포토미션 생성 작업의 결과라 반영하지 않음 jobId={}", jobId);
            return;
        }

        Optional<PhotoMissionGenerateResponse> response = readResponse(result);
        if (response.isEmpty()) {
            fail(jobId, PhotoMissionGenerationRejectedException.INVALID_AI_RESULT);
            return;
        }

        try {
            transaction.executeWithoutResult(status -> {
                if (terminationService.succeedMissionGeneration(jobId)) {
                    generationService.createMissions(tripId.get(), response.get(), LocalDateTime.now(clock));
                }
            });
        } catch (PhotoMissionGenerationRejectedException e) {
            log.warn("포토미션 생성 결과를 반영할 수 없음 jobId={}, reason={}", jobId, e.getMessage());
            fail(jobId, e.getErrorCode());
        }
    }

    private Optional<Long> findQueuedTripId(Long jobId) {
        return readOnlyTransaction.execute(status -> jobRepository.findById(jobId)
                .filter(job -> job.getStatus() == MissionGenerationStatus.QUEUED)
                .map(job -> job.getTrip().getId()));
    }

    private Optional<PhotoMissionGenerateResponse> readResponse(AiResultEnvelope result) {
        try {
            return Optional.ofNullable(objectMapper.treeToValue(result.result(), PhotoMissionGenerateResponse.class));
        } catch (JacksonException e) {
            log.warn("포토미션 생성 결과 본문을 읽지 못함 jobId={}", result.jobId(), e);
            return Optional.empty();
        }
    }

    private void fail(Long jobId, String errorCode) {
        transaction.executeWithoutResult(status -> terminationService.failMissionGeneration(jobId, errorCode));
    }
}
