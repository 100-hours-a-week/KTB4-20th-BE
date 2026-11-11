package com.planit.photomission.service;

import com.planit.photomission.domain.PhotoEvaluation;
import com.planit.photomission.domain.PhotoEvaluationResult;
import com.planit.photomission.repository.PhotoEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhotoEvaluationResultTransactionServiceTest {

    private static final LocalDateTime EVALUATED_AT =
            LocalDateTime.of(2026, 11, 9, 15, 0);

    private PhotoEvaluationRepository evaluationRepository;
    private PhotoMissionResultApplier resultApplier;
    private PhotoEvaluationResultTransactionService service;

    @BeforeEach
    void setUp() {
        evaluationRepository = mock(PhotoEvaluationRepository.class);
        resultApplier = mock(PhotoMissionResultApplier.class);
        service = new PhotoEvaluationResultTransactionService(
                evaluationRepository,
                resultApplier
        );
    }

    @DisplayName("QUEUED 결과를 최초로 완료하면 결과와 미션 상태를 한 번 반영한다")
    @Test
    void appliesFirstCompletedResult() {
        PhotoEvaluation evaluation = mock(PhotoEvaluation.class);
        PhotoEvaluationCompletedResult result = completedResult();
        whenCompleteQueued(result, 1);
        when(evaluationRepository.findById(51L))
                .thenReturn(Optional.of(evaluation));

        service.applyCompleted(result);

        verify(resultApplier).apply(evaluation, EVALUATED_AT);
    }

    @DisplayName("이미 종료된 결과를 다시 받으면 미션 상태를 중복 반영하지 않는다")
    @Test
    void ignoresDuplicateCompletedResult() {
        PhotoEvaluation evaluation = mock(PhotoEvaluation.class);
        PhotoEvaluationCompletedResult result = completedResult();
        whenCompleteQueued(result, 0);
        when(evaluationRepository.findById(51L))
                .thenReturn(Optional.of(evaluation));

        service.applyCompleted(result);

        verify(resultApplier, never()).apply(evaluation, EVALUATED_AT);
    }

    @DisplayName("존재하지 않는 판정 결과는 중복 메시지로 숨기지 않는다")
    @Test
    void rejectsUnknownEvaluation() {
        PhotoEvaluationCompletedResult result = completedResult();
        whenCompleteQueued(result, 0);
        when(evaluationRepository.findById(51L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyCompleted(result))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("포토미션 판정 작업을 찾을 수 없습니다");
    }

    @DisplayName("QUEUED 작업의 최종 실행 실패는 최초 한 번만 반영한다")
    @Test
    void appliesFinalFailureOnce() {
        PhotoEvaluationFailedResult result = new PhotoEvaluationFailedResult(
                51L,
                "AI_ATTEMPT_EXHAUSTED",
                EVALUATED_AT
        );
        when(evaluationRepository.failQueued(
                51L,
                "AI_ATTEMPT_EXHAUSTED",
                EVALUATED_AT
        )).thenReturn(1);

        service.applyFailed(result);

        when(evaluationRepository.failQueued(
                51L,
                "AI_ATTEMPT_EXHAUSTED",
                EVALUATED_AT
        )).thenReturn(0);
        when(evaluationRepository.existsById(51L)).thenReturn(true);

        service.applyFailed(result);
    }

    @DisplayName("완료 결과에 판정 결과가 없으면 DB 갱신 전에 거부한다")
    @Test
    void rejectsCompletedResultWithoutDecision() {
        assertThatThrownBy(() -> new PhotoEvaluationCompletedResult(
                51L,
                null,
                null,
                BigDecimal.valueOf(95),
                "[]",
                null,
                null,
                EVALUATED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI 판정 결과는 필수입니다");
    }

    @DisplayName("최종 실패 결과에 오류 코드가 없으면 DB 갱신 전에 거부한다")
    @Test
    void rejectsFailedResultWithoutErrorCode() {
        assertThatThrownBy(() -> new PhotoEvaluationFailedResult(
                51L,
                " ",
                EVALUATED_AT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI 실행 오류 코드는 필수입니다");
    }

    private PhotoEvaluationCompletedResult completedResult() {
        return new PhotoEvaluationCompletedResult(
                51L,
                PhotoEvaluationResult.SUCCESS,
                null,
                BigDecimal.valueOf(95),
                "[\"landmark\"]",
                BigDecimal.valueOf(92),
                null,
                EVALUATED_AT
        );
    }

    private void whenCompleteQueued(
            PhotoEvaluationCompletedResult result,
            int updated
    ) {
        when(evaluationRepository.completeQueued(
                result.evaluationId(),
                result.result(),
                result.reason(),
                result.matchScore(),
                result.detectedLabelsJson(),
                result.landmarkConfidence(),
                result.retryHint(),
                result.evaluatedAt()
        )).thenReturn(updated);
    }
}
