package com.planit.photomission.service;

import com.planit.image.storage.ImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhotoMissionPhotoManagementServiceTest {

    private ImageStorage imageStorage;
    private PhotoMissionPhotoManagementTransactionService transactionService;
    private PhotoMissionPhotoManagementService service;

    @BeforeEach
    void setUp() {
        imageStorage = mock(ImageStorage.class);
        transactionService = mock(
                PhotoMissionPhotoManagementTransactionService.class
        );
        service = new PhotoMissionPhotoManagementService(
                transactionService,
                imageStorage
        );
    }

    @DisplayName("DB에서 사진을 삭제한 뒤 저장소 객체를 삭제한다")
    @Test
    void deletesPhotoAndStoredObject() {
        when(transactionService.delete("user-id", 31L, 41L))
                .thenReturn("missions/31/photo.jpg");

        service.delete("user-id", 31L, 41L);

        verify(imageStorage).delete("missions/31/photo.jpg");
    }

    @DisplayName("저장소 객체 삭제가 실패해도 DB 삭제 결과를 유지한다")
    @Test
    void keepsDatabaseDeletionWhenStorageDeleteFails() {
        when(transactionService.delete("user-id", 31L, 41L))
                .thenReturn("missions/31/photo.jpg");
        doThrow(new RuntimeException("S3 오류"))
                .when(imageStorage).delete("missions/31/photo.jpg");

        assertThatCode(() -> service.delete("user-id", 31L, 41L))
                .doesNotThrowAnyException();
    }
}
