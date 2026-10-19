package com.planit.photomission.service;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.image.config.ImageProperties;
import com.planit.image.service.ImageValidator;
import com.planit.image.storage.ImageStorage;
import com.planit.photomission.ai.PhotoMissionVerifyResponse;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
public class PhotoMissionSubmissionService {

    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    private final ImageValidator imageValidator;
    private final ImageStorage imageStorage;
    private final PhotoMissionSubmissionTransactionService transactionService;
    private final PhotoMissionVerificationService verificationService;
    private final ImageProperties imageProperties;

    public PhotoMissionSubmissionService(
            ImageValidator imageValidator,
            ImageStorage imageStorage,
            PhotoMissionSubmissionTransactionService transactionService,
            PhotoMissionVerificationService verificationService,
            ImageProperties imageProperties
    ) {
        this.imageValidator = imageValidator;
        this.imageStorage = imageStorage;
        this.transactionService = transactionService;
        this.verificationService = verificationService;
        this.imageProperties = imageProperties;
    }

    public PhotoMissionVerifyResponse submitAndVerify(
            String userPublicId,
            Long missionId,
            UUID idempotencyKey,
            MultipartFile file,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        StoredImage image = storeImage(missionId, file, latitude, longitude);

        Long photoId;
        try {
            photoId = transactionService.save(
                    userPublicId,
                    missionId,
                    idempotencyKey,
                    image.imageKey(),
                    image.originalFilename(),
                    image.mimeType(),
                    image.sizeBytes(),
                    image.latitude(),
                    image.longitude()
            );
        } catch (RuntimeException exception) {
            compensateDelete(image.imageKey());
            throw exception;
        }

        return verificationService.verify(photoId);
    }

    public Long addGroupPhoto(
            String userPublicId,
            Long missionId,
            UUID idempotencyKey,
            MultipartFile file,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        StoredImage image = storeImage(missionId, file, latitude, longitude);
        try {
            return transactionService.saveAdditionalGroupPhoto(
                    userPublicId,
                    missionId,
                    idempotencyKey,
                    image.imageKey(),
                    image.originalFilename(),
                    image.mimeType(),
                    image.sizeBytes(),
                    image.latitude(),
                    image.longitude()
            );
        } catch (RuntimeException exception) {
            compensateDelete(image.imageKey());
            throw exception;
        }
    }

    private StoredImage storeImage(
            Long missionId,
            MultipartFile file,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        validateCoordinates(latitude, longitude);
        byte[] content = readContent(file);
        String mimeType = imageValidator.validate(
                content,
                file.getContentType(),
                imageProperties.maxMissionBytes()
        );
        String imageKey = createImageKey(missionId, mimeType);
        imageStorage.put(imageKey, content, mimeType);
        boolean hasCoordinates = completeCoordinates(latitude, longitude);
        return new StoredImage(
                imageKey,
                file.getOriginalFilename(),
                mimeType,
                content.length,
                hasCoordinates ? latitude : null,
                hasCoordinates ? longitude : null
        );
    }

    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    exception
            );
        }
    }

    private boolean completeCoordinates(
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        return latitude != null && longitude != null;
    }

    private void validateCoordinates(
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        if ((latitude == null) != (longitude == null)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (latitude == null) {
            return;
        }
        if (latitude.compareTo(MIN_LATITUDE) < 0
                || latitude.compareTo(MAX_LATITUDE) > 0
                || longitude.compareTo(MIN_LONGITUDE) < 0
                || longitude.compareTo(MAX_LONGITUDE) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private String createImageKey(Long missionId, String mimeType) {
        String extension = switch (mimeType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 포토미션 이미지 형식입니다"
            );
        };
        return "missions/" + missionId + "/" + UUID.randomUUID() + extension;
    }

    private void compensateDelete(String imageKey) {
        try {
            imageStorage.delete(imageKey);
        } catch (RuntimeException exception) {
            log.error("포토미션 이미지 보상 삭제에 실패했습니다.", exception);
            Sentry.captureException(exception);
        }
    }

    private record StoredImage(
            String imageKey,
            String originalFilename,
            String mimeType,
            int sizeBytes,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }
}
