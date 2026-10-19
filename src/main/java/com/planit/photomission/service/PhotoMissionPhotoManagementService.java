package com.planit.photomission.service;

import com.planit.image.storage.ImageStorage;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PhotoMissionPhotoManagementService {

    private final PhotoMissionPhotoManagementTransactionService
            transactionService;
    private final ImageStorage imageStorage;

    public PhotoMissionPhotoManagementService(
            PhotoMissionPhotoManagementTransactionService transactionService,
            ImageStorage imageStorage
    ) {
        this.transactionService = transactionService;
        this.imageStorage = imageStorage;
    }

    public void delete(
            String userPublicId,
            Long missionId,
            Long photoId
    ) {
        String imageKey = transactionService.delete(
                userPublicId,
                missionId,
                photoId
        );
        try {
            imageStorage.delete(imageKey);
        } catch (RuntimeException exception) {
            log.error("포토미션 사진 저장소 객체 삭제에 실패했습니다.", exception);
            Sentry.captureException(exception);
        }
    }
}
