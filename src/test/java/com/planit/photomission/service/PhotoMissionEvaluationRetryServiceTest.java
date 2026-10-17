package com.planit.photomission.service;

import com.planit.photomission.domain.MissionPhoto;
import com.planit.photomission.domain.PhotoEvaluation;
import com.planit.photomission.domain.PhotoEvaluationExecutionStatus;
import com.planit.photomission.repository.PhotoEvaluationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhotoMissionEvaluationRetryServiceTest {

    private final PhotoEvaluationRepository evaluationRepository =
            mock(PhotoEvaluationRepository.class);
    private final PhotoMissionVerificationService verificationService =
            mock(PhotoMissionVerificationService.class);
    private final PhotoMissionVerificationTransactionService transactionService =
            mock(PhotoMissionVerificationTransactionService.class);
    private final PhotoMissionEvaluationRetryService service =
            new PhotoMissionEvaluationRetryService(
                    evaluationRepository,
                    verificationService,
                    transactionService
            );

    @DisplayName("재시도 시각이 지난 판정을 모두 재실행하고 개별 오류가 다음 작업을 막지 않는다")
    @Test
    void retriesDueEvaluationsIndependently() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 17, 10, 0);
        PhotoEvaluation first = pendingEvaluation(51L);
        PhotoEvaluation second = pendingEvaluation(52L);
        when(evaluationRepository
                .findTop5ByExecutionStatusAndRetryAfterAtLessThanEqualOrderByRetryAfterAtAsc(
                        PhotoEvaluationExecutionStatus.RETRY_PENDING,
                        now
                )).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("첫 번째 재시도 실패"))
                .when(verificationService).retry(51L, now);

        service.retryDue(now);

        verify(verificationService).retry(51L, now);
        verify(verificationService).retry(52L, now);
    }

    @DisplayName("5분 이상 RUNNING인 판정을 재시도 상태로 복구한다")
    @Test
    void recoversStaleRunningEvaluations() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 17, 10, 0);
        LocalDateTime staleBefore = now.minusMinutes(5);
        PhotoEvaluation stale = runningEvaluation(53L, staleBefore);
        when(evaluationRepository
                .findTop5ByExecutionStatusAndRequestedAtLessThanEqualOrderByRequestedAtAsc(
                        PhotoEvaluationExecutionStatus.RUNNING,
                        staleBefore
                )).thenReturn(List.of(stale));

        service.retryDue(now);

        verify(transactionService).recoverStaleRunning(
                53L,
                staleBefore,
                now
        );
    }

    private PhotoEvaluation pendingEvaluation(Long id) {
        PhotoEvaluation evaluation = PhotoEvaluation.start(
                mock(MissionPhoto.class),
                1,
                LocalDateTime.of(2026, 10, 17, 9, 59)
        );
        evaluation.waitForRetry(
                "HTTP_502",
                LocalDateTime.of(2026, 10, 17, 9, 59, 30)
        );
        ReflectionTestUtils.setField(evaluation, "id", id);
        return evaluation;
    }

    private PhotoEvaluation runningEvaluation(
            Long id,
            LocalDateTime requestedAt
    ) {
        PhotoEvaluation evaluation = PhotoEvaluation.start(
                mock(MissionPhoto.class),
                1,
                requestedAt
        );
        ReflectionTestUtils.setField(evaluation, "id", id);
        return evaluation;
    }
}
