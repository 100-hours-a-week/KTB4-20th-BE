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

    private final PhotoEvaluationRepository evaluationRepository;
    private final PhotoMissionVerificationService verificationService;

    public void retryDue(LocalDateTime now) {
        evaluationRepository
                .findTop5ByExecutionStatusAndRetryAfterAtLessThanEqualOrderByRetryAfterAtAsc(
                        PhotoEvaluationExecutionStatus.RETRY_PENDING,
                        now
                )
                .forEach(evaluation -> retry(evaluation.getId(), now));
    }

    private void retry(Long evaluationId, LocalDateTime now) {
        try {
            verificationService.retry(evaluationId, now);
        } catch (RuntimeException exception) {
            log.error(
                    "포토미션 AI 판정 재시도에 실패했습니다. evaluationId={}",
                    evaluationId,
                    exception
            );
            Sentry.captureException(exception);
        }
    }
}
