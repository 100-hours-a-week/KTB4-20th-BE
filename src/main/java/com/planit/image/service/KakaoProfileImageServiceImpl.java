package com.planit.image.service;

import com.planit.auth.kakao.KakaoUserResponse;
import com.planit.domain.ImageFile;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.image.config.ImageProperties;
import com.planit.image.storage.ImageStorage;
import com.planit.repository.ImageFileRepository;
import io.sentry.Sentry;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@Slf4j
public class KakaoProfileImageServiceImpl
        implements KakaoProfileImageService {

    private final RestClient restClient;
    private final ImageValidator imageValidator;
    private final ImageStorage imageStorage;
    private final ImageFileRepository imageFileRepository;
    private final ImageProperties imageProperties;

    public KakaoProfileImageServiceImpl(
            @Qualifier("kakaoProfileImageRestClient")
            RestClient restClient,
            ImageValidator imageValidator,
            ImageStorage imageStorage,
            ImageFileRepository imageFileRepository,
            ImageProperties imageProperties
    ) {
        this.restClient = restClient;
        this.imageValidator = imageValidator;
        this.imageStorage = imageStorage;
        this.imageFileRepository = imageFileRepository;
        this.imageProperties = imageProperties;
    }

    @Override
    public Optional<ImageFile> importIfPresent(
            UUID userPublicId,
            KakaoUserResponse kakaoUser
    ) {
        String imageUrl = kakaoUser.customProfileImageUrl();
        if (imageUrl == null) {
            return Optional.empty();
        }

        try {
            return importImage(userPublicId, imageUrl);
        } catch (BusinessException exception) {
            log.warn(
                    "카카오 프로필 이미지 검증에 실패했습니다. code={}",
                    exception.getErrorCode().getCode()
            );
            return Optional.empty();
        } catch (RuntimeException exception) {
            log.error("카카오 프로필 이미지 가져오기에 실패했습니다.", exception);
            Sentry.captureException(exception);
            return Optional.empty();
        }
    }

    private Optional<ImageFile> importImage(
            UUID userPublicId,
            String imageUrl
    ) {
        DownloadedImage downloadedImage = downloadImage(imageUrl);
        byte[] content = downloadedImage.content();

        String mimeType = imageValidator.validate(
                content,
                downloadedImage.contentType(),
                imageProperties.maxProfileBytes()
        );

        String imageKey = createImageKey(
                userPublicId,
                mimeType
        );
        imageStorage.put(
                imageKey,
                content,
                mimeType
        );

        try {
            ImageFile imageFile = ImageFile.profile(
                    imageKey,
                    mimeType,
                    content.length
            );
            ImageFile savedImage = imageFileRepository.save(imageFile);
            registerRollbackCompensation(imageKey);

            return Optional.of(savedImage);
        } catch (RuntimeException exception) {
            compensateDelete(imageKey);
            throw exception;
        }
    }

    private DownloadedImage downloadImage(String imageUrl) {
        int maxBytes = imageProperties.maxProfileBytes();
        return restClient
                .get()
                .uri(imageUrl)
                .exchange((request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw new RestClientException(
                                "카카오 프로필 이미지 다운로드에 실패했습니다."
                        );
                    }

                    long contentLength = response
                            .getHeaders()
                            .getContentLength();
                    if (contentLength > maxBytes) {
                        throw new BusinessException(
                                ErrorCode.FILE_TOO_LARGE
                        );
                    }

                    byte[] content;
                    try (InputStream inputStream = response.getBody()) {
                        content = inputStream.readNBytes(maxBytes + 1);
                    } catch (IOException exception) {
                        throw new RestClientException(
                                "카카오 프로필 이미지 응답을 읽지 못했습니다.",
                                exception
                        );
                    }
                    if (content.length > maxBytes) {
                        throw new BusinessException(
                                ErrorCode.FILE_TOO_LARGE
                        );
                    }

                    MediaType contentType = response
                            .getHeaders()
                            .getContentType();
                    return new DownloadedImage(
                            content,
                            contentType == null
                                    ? null
                                    : contentType.toString()
                    );
                });
    }

    private void registerRollbackCompensation(String imageKey) {
        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            compensateDelete(imageKey);
                        }
                    }
                }
        );
    }

    private void compensateDelete(String imageKey) {
        try {
            imageStorage.delete(imageKey);
        } catch (RuntimeException exception) {
            log.error("카카오 프로필 이미지 보상 삭제에 실패했습니다.", exception);
            Sentry.captureException(exception);
        }
    }

    private String createImageKey(
            UUID userPublicId,
            String mimeType
    ) {
        String extension = switch (mimeType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException(
                    "Unsupported image MIME type"
            );
        };

        return "profiles/"
                + userPublicId
                + "/"
                + UUID.randomUUID()
                + extension;
    }

    private record DownloadedImage(
            byte[] content,
            String contentType
    ) {
    }
}
