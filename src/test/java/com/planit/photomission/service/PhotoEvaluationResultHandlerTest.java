package com.planit.photomission.service;

import com.planit.mq.job.AiJobType;
import com.planit.mq.result.AiResultEnvelope;
import com.planit.mq.result.AiResultStatus;
import com.planit.photomission.ai.PhotoMissionVerifyResponse;
import com.planit.photomission.domain.PhotoEvaluationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PhotoEvaluationResultHandlerTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-11-12T06:00:00Z");
    private static final LocalDateTime EVALUATED_AT =
            LocalDateTime.ofInstant(NOW, SEOUL);

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private PhotoEvaluationResultTransactionService transactionService;
    private PhotoEvaluationResultHandler handler;

    @BeforeEach
    void setUp() {
        transactionService = mock(PhotoEvaluationResultTransactionService.class);
        handler = new PhotoEvaluationResultHandler(
                transactionService,
                objectMapper,
                Clock.fixed(NOW, SEOUL)
        );
    }

    @DisplayName("PHOTO_JUDGE 결과 Handler로 등록된다")
    @Test
    void supportsPhotoJudge() {
        assertThat(handler.jobType()).isEqualTo(AiJobType.PHOTO_JUDGE);
    }

    @DisplayName("AI 성공 결과를 포토미션 판정 결과로 변환해 반영한다")
    @Test
    void appliesSucceededResult() {
        handler.handle(succeededResult(), 1);

        ArgumentCaptor<PhotoEvaluationCompletedResult> captor =
                ArgumentCaptor.forClass(PhotoEvaluationCompletedResult.class);
        verify(transactionService).applyCompleted(captor.capture());
        PhotoEvaluationCompletedResult result = captor.getValue();
        assertThat(result.evaluationId()).isEqualTo(51L);
        assertThat(result.executionAttemptCount()).isEqualTo(2);
        assertThat(result.result()).isEqualTo(PhotoEvaluationResult.SUCCESS);
        assertThat(result.reason()).isNull();
        assertThat(result.matchScore()).isEqualByComparingTo("95.0");
        assertThat(objectMapper.readTree(result.detectedLabelsJson()))
                .isEqualTo(objectMapper.readTree("[{\"name\":\"첨성대\",\"matched\":true}]"));
        assertThat(result.landmarkConfidence()).isEqualByComparingTo("92.0");
        assertThat(result.retryHint()).isNull();
        assertThat(result.evaluatedAt()).isEqualTo(EVALUATED_AT);
        verifyNoMoreInteractions(transactionService);
    }

    @DisplayName("AI 실패 결과를 포토미션 판정 최종 실패로 반영한다")
    @Test
    void appliesFailedResult() {
        handler.handle(new AiResultEnvelope(
                1,
                51L,
                AiJobType.PHOTO_JUDGE,
                "request-1",
                AiResultStatus.FAILED,
                "AI_TIMEOUT",
                null
        ), 2);

        verify(transactionService).applyFailed(new PhotoEvaluationFailedResult(
                51L,
                3,
                "AI_TIMEOUT",
                EVALUATED_AT
        ));
        verifyNoMoreInteractions(transactionService);
    }

    @DisplayName("읽을 수 없는 AI 성공 결과는 INVALID_AI_RESULT로 종료한다")
    @Test
    void failsInvalidSucceededResult() {
        AiResultEnvelope invalid = new AiResultEnvelope(
                1,
                51L,
                AiJobType.PHOTO_JUDGE,
                "request-1",
                AiResultStatus.SUCCEEDED,
                null,
                objectMapper.readTree("{\"result\":\"unknown\"}")
        );

        handler.handle(invalid, 0);

        verify(transactionService).applyFailed(new PhotoEvaluationFailedResult(
                51L,
                1,
                "INVALID_AI_RESULT",
                EVALUATED_AT
        ));
        verifyNoMoreInteractions(transactionService);
    }

    @DisplayName("결과 변환 중 예상하지 못한 오류는 다시 처리할 수 있도록 전파한다")
    @Test
    void rethrowsUnexpectedMappingError() {
        ObjectMapper brokenMapper = mock(ObjectMapper.class);
        when(brokenMapper.treeToValue(any(), eq(PhotoMissionVerifyResponse.class)))
                .thenThrow(new IllegalStateException("unexpected"));
        PhotoEvaluationResultHandler brokenHandler =
                new PhotoEvaluationResultHandler(
                        transactionService,
                        brokenMapper,
                        Clock.fixed(NOW, SEOUL)
                );

        assertThatThrownBy(() -> brokenHandler.handle(succeededResult(), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("unexpected");
        verifyNoMoreInteractions(transactionService);
    }

    private AiResultEnvelope succeededResult() {
        return new AiResultEnvelope(
                1,
                51L,
                AiJobType.PHOTO_JUDGE,
                "request-1",
                AiResultStatus.SUCCEEDED,
                null,
                objectMapper.readTree("""
                        {
                          "result": "success",
                          "reason": null,
                          "match_score": 95.0,
                          "detected_labels": [
                            {"name": "첨성대", "matched": true}
                          ],
                          "landmark_confidence": 92.0,
                          "retry_hint": null
                        }
                        """)
        );
    }
}
