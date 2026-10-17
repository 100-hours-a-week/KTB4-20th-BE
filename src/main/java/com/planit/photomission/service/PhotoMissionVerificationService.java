package com.planit.photomission.service;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.photomission.ai.PhotoMissionAiClient;
import com.planit.photomission.ai.PhotoMissionVerifyResponse;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;

@Service
public class PhotoMissionVerificationService {

    private static final int DEFAULT_RETRY_DELAY_SECONDS = 30;

    private final PhotoMissionVerificationTransactionService
            transactionService;
    private final PhotoMissionAiClient aiClient;

    public PhotoMissionVerificationService(
            PhotoMissionVerificationTransactionService transactionService,
            PhotoMissionAiClient aiClient
    ) {
        this.transactionService = transactionService;
        this.aiClient = aiClient;
    }

    public PhotoMissionVerifyResponse verify(Long missionPhotoId) {
        PhotoMissionVerificationTransactionService.PreparedVerification
                prepared = transactionService.prepare(missionPhotoId);

        try {
            PhotoMissionVerifyResponse response = aiClient.verify(
                    prepared.missionId(),
                    prepared.request()
            );
            transactionService.complete(
                    prepared.evaluationId(),
                    response
            );
            return response;
        } catch (RuntimeException exception) {
            recordExecutionError(prepared.evaluationId(), exception);
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    exception
            );
        }
    }

    private void recordExecutionError(
            Long evaluationId,
            RuntimeException exception
    ) {
        String errorCode = errorCode(exception);
        if (isRetryable(exception)) {
            transactionService.waitForRetry(
                    evaluationId,
                    errorCode,
                    retryAfterAt(exception)
            );
            return;
        }
        transactionService.fail(evaluationId, errorCode);
    }

    private boolean isRetryable(RuntimeException exception) {
        if (!(exception instanceof RestClientResponseException response)) {
            return true;
        }
        int status = response.getStatusCode().value();
        return status == HttpStatus.TOO_MANY_REQUESTS.value()
                || status == HttpStatus.BAD_GATEWAY.value()
                || status == HttpStatus.GATEWAY_TIMEOUT.value();
    }

    private String errorCode(RuntimeException exception) {
        if (exception instanceof RestClientResponseException response) {
            return "HTTP_" + response.getStatusCode().value();
        }
        return exception.getClass().getSimpleName();
    }

    private LocalDateTime retryAfterAt(RuntimeException exception) {
        int delaySeconds = DEFAULT_RETRY_DELAY_SECONDS;
        if (exception instanceof RestClientResponseException response
                && response.getStatusCode().value()
                == HttpStatus.TOO_MANY_REQUESTS.value()) {
            String retryAfter = response.getResponseHeaders() == null
                    ? null
                    : response.getResponseHeaders().getFirst("Retry-After");
            delaySeconds = parseRetryAfter(retryAfter);
        }
        return LocalDateTime.now().plusSeconds(delaySeconds);
    }

    private int parseRetryAfter(String retryAfter) {
        if (retryAfter == null) {
            return DEFAULT_RETRY_DELAY_SECONDS;
        }
        try {
            int seconds = Integer.parseInt(retryAfter);
            return seconds >= 0 ? seconds : DEFAULT_RETRY_DELAY_SECONDS;
        } catch (NumberFormatException ignored) {
            return DEFAULT_RETRY_DELAY_SECONDS;
        }
    }
}
