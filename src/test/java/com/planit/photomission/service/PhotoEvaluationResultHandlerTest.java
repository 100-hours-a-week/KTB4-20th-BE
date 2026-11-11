package com.planit.photomission.service;

import com.planit.photomission.domain.PhotoEvaluationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PhotoEvaluationResultHandlerTest {

    private static final LocalDateTime EVALUATED_AT =
            LocalDateTime.of(2026, 11, 9, 15, 0);

    private PhotoEvaluationResultTransactionService transactionService;
    private PhotoEvaluationResultHandler handler;

    @BeforeEach
    void setUp() {
        transactionService = mock(
                PhotoEvaluationResultTransactionService.class
        );
        handler = new PhotoEvaluationResultHandler(transactionService);
    }

    @DisplayName("완료 결과를 포토미션 결과 트랜잭션에 전달한다")
    @Test
    void handlesCompletedResult() {
        PhotoEvaluationCompletedResult result = completedResult();
        handler.handle(result);

        verify(transactionService).applyCompleted(result);
    }

    @DisplayName("최종 실패 결과를 포토미션 결과 트랜잭션에 전달한다")
    @Test
    void handlesFailedResult() {
        PhotoEvaluationFailedResult result = new PhotoEvaluationFailedResult(
                51L,
                "AI_ATTEMPT_EXHAUSTED",
                EVALUATED_AT
        );
        handler.handle(result);

        verify(transactionService).applyFailed(result);
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
}
