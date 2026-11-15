package com.planit.photomission.service;

import com.planit.mq.job.AiJobType;
import com.planit.mq.result.AiResultEnvelope;
import com.planit.mq.result.AiResultHandler;
import com.planit.photomission.ai.PhotoMissionVerifyResponse;
import com.planit.photomission.domain.PhotoEvaluationReason;
import com.planit.photomission.domain.PhotoEvaluationResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class PhotoEvaluationResultHandler implements AiResultHandler {

    public static final String INVALID_AI_RESULT = "INVALID_AI_RESULT";

    private final PhotoEvaluationResultTransactionService transactionService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PhotoEvaluationResultHandler(
            PhotoEvaluationResultTransactionService transactionService,
            ObjectMapper objectMapper,
            @Qualifier("photoMissionClock") Clock clock
    ) {
        this.transactionService = transactionService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public AiJobType jobType() {
        return AiJobType.PHOTO_JUDGE;
    }

    @Override
    public void handle(AiResultEnvelope result, int retryCount) {
        int executionAttemptCount = retryCount + 1;
        LocalDateTime evaluatedAt = LocalDateTime.now(clock);
        if (!result.isSucceeded()) {
            transactionService.applyFailed(new PhotoEvaluationFailedResult(
                    result.jobId(),
                    executionAttemptCount,
                    result.errorKind(),
                    evaluatedAt
            ));
            return;
        }

        PhotoEvaluationCompletedResult completedResult;
        try {
            completedResult = toCompletedResult(
                    result,
                    executionAttemptCount,
                    evaluatedAt
            );
        } catch (JacksonException | IllegalArgumentException exception) {
            transactionService.applyFailed(new PhotoEvaluationFailedResult(
                    result.jobId(),
                    executionAttemptCount,
                    INVALID_AI_RESULT,
                    evaluatedAt
            ));
            return;
        }
        transactionService.applyCompleted(completedResult);
    }

    private PhotoEvaluationCompletedResult toCompletedResult(
            AiResultEnvelope result,
            int executionAttemptCount,
            LocalDateTime evaluatedAt
    ) {
        PhotoMissionVerifyResponse response = objectMapper.treeToValue(
                result.result(),
                PhotoMissionVerifyResponse.class
        );
        if (response.detectedLabels() == null) {
            throw new IllegalArgumentException("인식 항목은 필수입니다");
        }
        return new PhotoEvaluationCompletedResult(
                result.jobId(),
                executionAttemptCount,
                mapResult(response.result()),
                mapReason(response.reason()),
                response.matchScore(),
                objectMapper.writeValueAsString(response.detectedLabels()),
                response.retryHint(),
                evaluatedAt
        );
    }

    private PhotoEvaluationResult mapResult(String result) {
        if (result == null) {
            throw new IllegalArgumentException("AI 판정 결과는 필수입니다");
        }
        return switch (result) {
            case "success" -> PhotoEvaluationResult.SUCCESS;
            case "retry" -> PhotoEvaluationResult.RETRY;
            case "fail" -> PhotoEvaluationResult.FAIL;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 AI 판정 결과입니다: " + result
            );
        };
    }

    private PhotoEvaluationReason mapReason(String reason) {
        if (reason == null) {
            return null;
        }
        if ("location_mismatch".equals(reason)) {
            return PhotoEvaluationReason.LOCATION_MISMATCH;
        }
        throw new IllegalArgumentException(
                "지원하지 않는 AI 판정 사유입니다: " + reason
        );
    }
}
