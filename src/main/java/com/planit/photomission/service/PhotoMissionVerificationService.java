package com.planit.photomission.service;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.photomission.ai.PhotoMissionAiClient;
import com.planit.photomission.ai.PhotoMissionVerifyResponse;
import org.springframework.stereotype.Service;

@Service
public class PhotoMissionVerificationService {

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
            transactionService.fail(
                    prepared.evaluationId(),
                    exception.getClass().getSimpleName()
            );
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    exception
            );
        }
    }
}
