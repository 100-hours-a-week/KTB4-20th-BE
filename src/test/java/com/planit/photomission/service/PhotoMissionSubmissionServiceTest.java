package com.planit.photomission.service;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.image.config.ImageProperties;
import com.planit.image.service.ImageValidator;
import com.planit.image.storage.ImageStorage;
import com.planit.photomission.ai.PhotoMissionVerifyResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PhotoMissionSubmissionServiceTest {

    private ImageStorage imageStorage;
    private PhotoMissionSubmissionTransactionService transactionService;
    private PhotoMissionVerificationService verificationService;
    private PhotoMissionSubmissionService service;

    @BeforeEach
    void setUp() {
        imageStorage = mock(ImageStorage.class);
        transactionService = mock(PhotoMissionSubmissionTransactionService.class);
        verificationService = mock(PhotoMissionVerificationService.class);
        service = new PhotoMissionSubmissionService(
                new ImageValidator(),
                imageStorage,
                transactionService,
                verificationService,
                new ImageProperties(
                        URI.create("https://example.com/default.svg"),
                        "bucket",
                        "ap-northeast-2",
                        Duration.ofMinutes(5),
                        5 * 1024 * 1024,
                        5 * 1024 * 1024,
                        Duration.ofSeconds(3),
                        Duration.ofSeconds(5)
                )
        );
    }

    @DisplayName("사진을 저장하고 같은 요청에서 AI 판정 결과를 반환한다")
    @Test
    void submitsAndVerifiesPhoto() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        MockMultipartFile file = jpegFile();
        PhotoMissionVerifyResponse response = successResponse();
        when(transactionService.save(
                any(), any(), any(), any(), any(), any(), anyInt(), any(), any()
        )).thenReturn(41L);
        when(verificationService.verify(41L)).thenReturn(response);

        PhotoMissionVerifyResponse result = service.submitAndVerify(
                "550e8400-e29b-41d4-a716-446655440000",
                31L,
                idempotencyKey,
                file,
                BigDecimal.valueOf(35.8350),
                BigDecimal.valueOf(129.2188)
        );

        assertThat(result).isSameAs(response);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(imageStorage).put(
                keyCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(file.getBytes()),
                org.mockito.ArgumentMatchers.eq("image/jpeg")
        );
        assertThat(keyCaptor.getValue())
                .startsWith("missions/31/")
                .endsWith(".jpg");
        verify(verificationService).verify(41L);
    }

    @DisplayName("S3 저장 후 DB 저장이 실패하면 업로드한 객체를 삭제한다")
    @Test
    void deletesStoredImageWhenDatabaseSaveFails() {
        RuntimeException exception = new RuntimeException("DB 저장 실패");
        when(transactionService.save(
                any(), any(), any(), any(), any(), any(), anyInt(), any(), any()
        )).thenThrow(exception);

        assertThatThrownBy(() -> service.submitAndVerify(
                "550e8400-e29b-41d4-a716-446655440000",
                31L,
                UUID.randomUUID(),
                jpegFile(),
                null,
                null
        )).isSameAs(exception);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(imageStorage).delete(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).startsWith("missions/31/");
        verify(verificationService, never()).verify(any());
    }

    @DisplayName("위도와 경도 중 하나만 전달되면 업로드하지 않는다")
    @Test
    void rejectsIncompleteCoordinates() {
        assertInvalidCoordinates(BigDecimal.valueOf(35.8350), null);
        assertInvalidCoordinates(null, BigDecimal.valueOf(129.2188));

        verifyNoInteractions(imageStorage, transactionService, verificationService);
    }

    @DisplayName("허용 범위를 벗어난 좌표는 업로드하지 않는다")
    @ParameterizedTest
    @CsvSource({
            "90.0000001, 0",
            "-90.0000001, 0",
            "0, 180.0000001",
            "0, -180.0000001"
    })
    void rejectsCoordinatesOutsideEarthBounds(
            String latitude,
            String longitude
    ) {
        assertInvalidCoordinates(
                new BigDecimal(latitude),
                new BigDecimal(longitude)
        );

        verifyNoInteractions(imageStorage, transactionService, verificationService);
    }

    private void assertInvalidCoordinates(
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        assertThatThrownBy(() -> service.submitAndVerify(
                "550e8400-e29b-41d4-a716-446655440000",
                31L,
                UUID.randomUUID(),
                jpegFile(),
                latitude,
                longitude
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_REQUEST)
        );
    }

    private MockMultipartFile jpegFile() {
        return new MockMultipartFile(
                "photo",
                "photo.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00}
        );
    }

    private PhotoMissionVerifyResponse successResponse() {
        return new PhotoMissionVerifyResponse(
                "success",
                null,
                BigDecimal.valueOf(95),
                List.of(),
                BigDecimal.valueOf(95),
                null
        );
    }
}
