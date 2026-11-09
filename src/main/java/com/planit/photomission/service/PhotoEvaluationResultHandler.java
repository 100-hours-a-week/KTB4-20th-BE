package com.planit.photomission.service;

import org.springframework.stereotype.Component;

@Component
public class PhotoEvaluationResultHandler {

    private final PhotoEvaluationResultTransactionService transactionService;

    public PhotoEvaluationResultHandler(
            PhotoEvaluationResultTransactionService transactionService
    ) {
        this.transactionService = transactionService;
    }

    public PhotoEvaluationResultApplyOutcome handle(
            PhotoEvaluationCompletedResult result
    ) {
        return transactionService.applyCompleted(result);
    }

    public PhotoEvaluationResultApplyOutcome handle(
            PhotoEvaluationFailedResult result
    ) {
        return transactionService.applyFailed(result);
    }
}
