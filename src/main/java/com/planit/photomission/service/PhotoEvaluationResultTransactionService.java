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

    public PhotoEvaluationResultTransactionService(
            PhotoEvaluationRepository evaluationRepository,
            PhotoMissionResultApplier resultApplier
    ) {
        this.evaluationRepository = evaluationRepository;
        this.resultApplier = resultApplier;
    }

    @Transactional
    public PhotoEvaluationResultApplyOutcome applyCompleted(
            PhotoEvaluationCompletedResult result
    ) {
        int updated = evaluationRepository.completeQueued(
                result.evaluationId(),
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
            return PhotoEvaluationResultApplyOutcome.DUPLICATE;
        }

        resultApplier.apply(evaluation.get(), result.evaluatedAt());
        return PhotoEvaluationResultApplyOutcome.APPLIED;
    }

    @Transactional
    public PhotoEvaluationResultApplyOutcome applyFailed(
            PhotoEvaluationFailedResult result
    ) {
        int updated = evaluationRepository.failQueued(
                result.evaluationId(),
                result.errorCode(),
                result.failedAt()
        );
        if (updated == 1) {
            return PhotoEvaluationResultApplyOutcome.APPLIED;
        }
        if (!evaluationRepository.existsById(result.evaluationId())) {
            throw unknownEvaluation();
        }
        return PhotoEvaluationResultApplyOutcome.DUPLICATE;
    }

    private IllegalArgumentException unknownEvaluation() {
        return new IllegalArgumentException(
                "포토미션 판정 작업을 찾을 수 없습니다"
        );
    }
}
