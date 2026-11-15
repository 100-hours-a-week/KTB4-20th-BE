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

    public void handle(
            PhotoEvaluationCompletedResult result
    ) {
        transactionService.applyCompleted(result);
    }

    public void handle(
            PhotoEvaluationFailedResult result
    ) {
        transactionService.applyFailed(result);
    }
}
