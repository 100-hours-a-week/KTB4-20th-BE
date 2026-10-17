package com.planit.photomission.service;

import com.planit.photomission.domain.PhotoEvaluationExecutionStatus;
import com.planit.photomission.repository.PhotoEvaluationRepository;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoMissionEvaluationRetryService {

    private static final int STALE_RUNNING_MINUTES = 5;

    private final PhotoEvaluationRepository evaluationRepository;
    private final PhotoMissionVerificationService verificationService;
    private final PhotoMissionVerificationTransactionService transactionService;

    public void retryDue(LocalDateTime now) {
        LocalDateTime staleBefore = now.minusMinutes(STALE_RUNNING_MINUTES);
        evaluationRepository
                .findTop5ByExecutionStatusAndRequestedAtLessThanEqualOrderByRequestedAtAsc(
                        PhotoEvaluationExecutionStatus.RUNNING,
                        staleBefore
                )
                .forEach(evaluation -> runSafely(
                        evaluation.getId(),
                        () -> transactionService.recoverStaleRunning(
                                evaluation.getId(),
                                staleBefore,
                                now
                        )
                ));
        evaluationRepository
                .findTop5ByExecutionStatusAndRetryAfterAtLessThanEqualOrderByRetryAfterAtAsc(
                        PhotoEvaluationExecutionStatus.RETRY_PENDING,
                        now
                )
                .forEach(evaluation -> runSafely(
                        evaluation.getId(),
                        () -> verificationService.retry(
                                evaluation.getId(),
                                now
                        )
                ));
    }

    private void runSafely(Long evaluationId, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException exception) {
            log.error(
                    "포토미션 AI 판정 복구에 실패했습니다. evaluationId={}",
                    evaluationId,
                    exception
            );
            Sentry.captureException(exception);
        }
    }
}
