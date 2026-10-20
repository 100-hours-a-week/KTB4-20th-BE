package com.planit.image.storage;

import com.planit.image.config.ImageProperties;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3ImageStorageTest {

    private static final String BUCKET = "test-bucket";
    private static final Duration READ_URL_TTL = Duration.ofMinutes(5);

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private S3ImageStorage imageStorage;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        s3Presigner = mock(S3Presigner.class);

        ImageProperties properties = mock(ImageProperties.class);
        when(properties.bucket()).thenReturn(BUCKET);
        when(properties.readUrlTtl()).thenReturn(READ_URL_TTL);

        imageStorage = new S3ImageStorage(
                s3Client,
                s3Presigner,
                properties
        );
    }

    @DisplayName("이미지 byte 배열을 지정한 S3 Key에 저장한다")
    @Test
    void putsImageIntoS3() throws IOException {
        byte[] content = {
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF
        };

        imageStorage.put(
                "profiles/user-id/profile.jpg",
                content,
                "image/jpeg"
        );

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor =
                ArgumentCaptor.forClass(RequestBody.class);

        verify(s3Client).putObject(
                requestCaptor.capture(),
                bodyCaptor.capture()
        );

        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.key())
                .isEqualTo("profiles/user-id/profile.jpg");
        assertThat(request.contentType()).isEqualTo("image/jpeg");
        assertThat(request.contentLength()).isEqualTo(3L);
        try (var inputStream = bodyCaptor
                .getValue()
                .contentStreamProvider()
                .newStream()) {
            assertThat(inputStream.readAllBytes())
                    .containsExactly(content);
        }
    }

    @DisplayName("설정된 만료시간으로 S3 조회 URL을 발급한다")
    @Test
    void createsReadUrl() throws Exception {
        PresignedGetObjectRequest presignedRequest =
                mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(
                URI.create(
                        "https://example.com/presigned-profile"
                ).toURL()
        );
        when(s3Presigner.presignGetObject(
                any(GetObjectPresignRequest.class)
        )).thenReturn(presignedRequest);

        String result = imageStorage.createReadUrl(
                "profiles/user-id/profile.jpg"
        );

        assertThat(result).isEqualTo(
                "https://example.com/presigned-profile"
        );

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        GetObjectPresignRequest.class
                );
        verify(s3Presigner).presignGetObject(
                requestCaptor.capture()
        );

        GetObjectPresignRequest request = requestCaptor.getValue();
        assertThat(request.signatureDuration())
                .isEqualTo(READ_URL_TTL);
        assertThat(request.getObjectRequest().bucket())
                .isEqualTo(BUCKET);
        assertThat(request.getObjectRequest().key())
                .isEqualTo("profiles/user-id/profile.jpg");
    }

    @DisplayName("지정한 S3 객체를 삭제한다")
    @Test
    void deletesImageFromS3() {
        imageStorage.delete("profiles/user-id/profile.jpg");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());

        DeleteObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.key())
                .isEqualTo("profiles/user-id/profile.jpg");
    }
}
