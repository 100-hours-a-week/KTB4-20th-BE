package com.planit.image.storage;

import com.planit.image.config.ImageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class S3ImageStorage implements ImageStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ImageProperties imageProperties;

    @Override
    public void put(
            String key,
            byte[] content,
            String contentType
    ) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(imageProperties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength((long) content.length)
                .build();

        s3Client.putObject(
                request,
                RequestBody.fromBytes(content)
        );
    }

    @Override
    public String createReadUrl(String key) {
        return createReadUrl(key, imageProperties.readUrlTtl());
    }

    @Override
    public String createReadUrl(
            String key,
            Duration signatureDuration
    ) {
        GetObjectRequest getObjectRequest =
                GetObjectRequest.builder()
                        .bucket(imageProperties.bucket())
                        .key(key)
                        .build();

        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(
                                signatureDuration
                        )
                        .getObjectRequest(getObjectRequest)
                        .build();

        return s3Presigner
                .presignGetObject(presignRequest)
                .url()
                .toExternalForm();
    }

    @Override
    public void delete(String key) {
        DeleteObjectRequest request =
                DeleteObjectRequest.builder()
                        .bucket(imageProperties.bucket())
                        .key(key)
                        .build();

        s3Client.deleteObject(request);
    }
}
