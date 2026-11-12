package com.planit.photomission.service;

import com.planit.photomission.domain.PhotoEvaluation;
import com.planit.photomission.repository.PhotoEvaluationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class PhotoEvaluationResultTransactionService {

    private final PhotoEvaluationRepository evaluationRepository;
    private final PhotoMissionResultApplier resultApplier;
    private final PhotoEvaluationNotificationService notificationService;

    public PhotoEvaluationResultTransactionService(
            PhotoEvaluationRepository evaluationRepository,
            PhotoMissionResultApplier resultApplier,
            PhotoEvaluationNotificationService notificationService
    ) {
        this.evaluationRepository = evaluationRepository;
        this.resultApplier = resultApplier;
        this.notificationService = notificationService;
    }

    @Transactional
    public void applyCompleted(
            PhotoEvaluationCompletedResult result
    ) {
        int updated = evaluationRepository.completeQueued(
                result.evaluationId(),
                result.executionAttemptCount(),
                result.result(),
                result.reason(),
                result.matchScore(),
                result.detectedLabelsJson(),
                result.landmarkConfidence(),
                result.retryHint(),
                result.evaluatedAt()
        );
        Optional<PhotoEvaluation> evaluation =
                evaluationRepository.findById(result.evaluationId());
        if (evaluation.isEmpty()) {
            throw unknownEvaluation();
        }
        if (updated == 0) {
            return;
        }

        resultApplier.apply(evaluation.get(), result.evaluatedAt());
        notificationService.notifyCompleted(
                evaluation.get(),
                result.evaluatedAt()
        );
    }

    @Transactional
    public void applyFailed(
            PhotoEvaluationFailedResult result
    ) {
        int updated = evaluationRepository.failQueued(
                result.evaluationId(),
                result.executionAttemptCount(),
                result.errorCode(),
                result.failedAt()
        );
        Optional<PhotoEvaluation> evaluation =
                evaluationRepository.findById(result.evaluationId());
        if (evaluation.isEmpty()) {
            throw unknownEvaluation();
        }
        if (updated == 1) {
            notificationService.notifyFailed(
                    evaluation.get(),
                    result.failedAt()
            );
            return;
        }
    }

    private IllegalArgumentException unknownEvaluation() {
        return new IllegalArgumentException(
                "포토미션 판정 작업을 찾을 수 없습니다"
        );
    }
}
