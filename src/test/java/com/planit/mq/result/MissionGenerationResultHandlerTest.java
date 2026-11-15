package com.planit.mq.result;

import com.planit.domain.Trip;
import com.planit.mq.job.AiJobTerminationService;
import com.planit.mq.job.AiJobType;
import com.planit.photomission.ai.PhotoMissionGenerateResponse;
import com.planit.photomission.domain.MissionGenerationJob;
import com.planit.photomission.domain.MissionGenerationStatus;
import com.planit.photomission.repository.MissionGenerationJobRepository;
import com.planit.photomission.service.PhotoMissionGenerationRejectedException;
import com.planit.photomission.service.PhotoMissionGenerationTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MissionGenerationResultHandlerTest {

    private static final long JOB_ID = 42L;
    private static final long TRIP_ID = 7L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-11-11T15:10:00Z");
    private static final String AI_RESULT = """
            {"missions":[{"mission_id":"m1","place_id":"google-1","description":"정문 앞에서 찍기",
                          "scope":"PERSONAL","primary_category":"역사·유적"}]}
            """;

    private final MissionGenerationJobRepository jobRepository = mock(MissionGenerationJobRepository.class);
    private final AiJobTerminationService terminationService = mock(AiJobTerminationService.class);
    private final PhotoMissionGenerationTransactionService generationService =
            mock(PhotoMissionGenerationTransactionService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private final MissionGenerationResultHandler handler = new MissionGenerationResultHandler(
            jobRepository,
            terminationService,
            generationService,
            jsonMapper,
            Clock.fixed(NOW, SEOUL),
            transactionManager
    );

    private final PhotoMissionGenerateResponse expectedResponse = new PhotoMissionGenerateResponse(List.of(
            new PhotoMissionGenerateResponse.Mission("m1", "google-1", "정문 앞에서 찍기", "PERSONAL", "역사·유적")
    ));

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        givenJob(MissionGenerationStatus.QUEUED);
        when(terminationService.succeedMissionGeneration(JOB_ID)).thenReturn(true);
    }

    @DisplayName("작업을 성공으로 바꾼 트랜잭션에서 AI가 만든 미션을 저장한다")
    @Test
    void createsMissionsAfterSucceeded() {
        handler.handle(succeeded(AI_RESULT), 0);

        var order = inOrder(terminationService, generationService, transactionManager);
        order.verify(terminationService).succeedMissionGeneration(JOB_ID);
        order.verify(generationService)
                .createMissions(TRIP_ID, expectedResponse, LocalDateTime.ofInstant(NOW, SEOUL));
        order.verify(transactionManager).commit(any());
    }

    @DisplayName("작업을 성공으로 바꾸지 못했으면 이미 끝난 작업이므로 미션을 저장하지 않는다")
    @Test
    void skipsWhenAlreadyFinished() {
        when(terminationService.succeedMissionGeneration(JOB_ID)).thenReturn(false);

        handler.handle(succeeded(AI_RESULT), 0);

        verify(generationService, never()).createMissions(anyLong(), any(), any());
    }

    @DisplayName("진행 중이 아닌 작업의 결과는 무시한다")
    @Test
    void ignoresFinishedJob() {
        givenJob(MissionGenerationStatus.SUCCEEDED);

        handler.handle(succeeded(AI_RESULT), 0);

        verifyNoInteractions(terminationService, generationService);
    }

    @DisplayName("AI 처리 실패 결과는 AI가 준 실패 원인으로 작업을 끝낸다")
    @Test
    void failsWithAiErrorKind() {
        handler.handle(new AiResultEnvelope(1, JOB_ID, AiJobType.PHOTO_MISSION_GENERATE, "m-1",
                AiResultStatus.FAILED, "AI_TIMEOUT", null), 1);

        verify(terminationService).failMissionGeneration(JOB_ID, "AI_TIMEOUT");
        verifyNoInteractions(jobRepository, generationService);
    }

    @DisplayName("결과 본문을 읽을 수 없으면 INVALID_AI_RESULT로 끝낸다")
    @Test
    void failsWhenResultUnreadable() {
        handler.handle(succeeded("{\"missions\":\"not a list\"}"), 0);

        verify(terminationService).failMissionGeneration(JOB_ID, PhotoMissionGenerationRejectedException.INVALID_AI_RESULT);
        verify(terminationService, never()).succeedMissionGeneration(anyLong());
    }

    @DisplayName("미션을 만들 수 없는 결과면 롤백하고 새 트랜잭션에서 그 원인으로 작업을 실패로 끝낸다")
    @Test
    void failsAfterRollbackWhenRejected() {
        doThrow(new PhotoMissionGenerationRejectedException(
                PhotoMissionGenerationRejectedException.MISSION_TARGET_NOT_FOUND, "확정된 여행 일정이 없습니다"))
                .when(generationService).createMissions(anyLong(), any(), any());

        handler.handle(succeeded(AI_RESULT), 0);

        var order = inOrder(transactionManager, terminationService);
        order.verify(transactionManager).rollback(any());
        order.verify(terminationService)
                .failMissionGeneration(JOB_ID, PhotoMissionGenerationRejectedException.MISSION_TARGET_NOT_FOUND);
        order.verify(transactionManager).commit(any());
    }

    @DisplayName("그 밖의 예외는 실패로 끝내지 않고 그대로 던져 다시 시도하게 한다")
    @Test
    void rethrowsUnexpectedError() {
        doThrow(new QueryTimeoutException("timeout"))
                .when(generationService).createMissions(anyLong(), any(), any());

        assertThatThrownBy(() -> handler.handle(succeeded(AI_RESULT), 0)).isInstanceOf(QueryTimeoutException.class);
        verify(terminationService, never()).failMissionGeneration(anyLong(), anyString());
    }

    private void givenJob(MissionGenerationStatus status) {
        Trip trip = mock(Trip.class);
        when(trip.getId()).thenReturn(TRIP_ID);
        MissionGenerationJob job = mock(MissionGenerationJob.class);
        when(job.getStatus()).thenReturn(status);
        when(job.getTrip()).thenReturn(trip);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
    }

    private AiResultEnvelope succeeded(String result) {
        return new AiResultEnvelope(1, JOB_ID, AiJobType.PHOTO_MISSION_GENERATE, "m-1",
                AiResultStatus.SUCCEEDED, null, jsonMapper.readTree(result));
    }
}
