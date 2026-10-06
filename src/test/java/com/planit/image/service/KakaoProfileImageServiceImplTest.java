package com.planit.image.service;

import com.planit.auth.kakao.KakaoUserResponse;
import com.planit.domain.ImageFile;
import com.planit.domain.ImagePurpose;
import com.planit.image.config.ImageProperties;
import com.planit.image.storage.ImageStorage;
import com.planit.repository.ImageFileRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class KakaoProfileImageServiceImplTest {

    private static final UUID USER_PUBLIC_ID = UUID.fromString(
            "01991f6e-7300-7b21-a3cc-1436db3df95e"
    );

    private MockRestServiceServer server;
    private ImageStorage imageStorage;
    private ImageFileRepository imageFileRepository;
    private ImageProperties imageProperties;
    private KakaoProfileImageService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        imageStorage = mock(ImageStorage.class);
        imageFileRepository = mock(ImageFileRepository.class);

        imageProperties = mock(ImageProperties.class);
        when(imageProperties.maxProfileBytes())
                .thenReturn(5 * 1024 * 1024);

        service = new KakaoProfileImageServiceImpl(
                builder.build(),
                new ImageValidator(),
                imageStorage,
                imageFileRepository,
                imageProperties
        );
    }

    @DisplayName("카카오 프로필 사진이 없으면 이미지를 생성하지 않는다")
    @Test
    void skipsWhenKakaoProfileImageIsMissing() {
        KakaoUserResponse kakaoUser = kakaoUser(
                null,
                true
        );

        Optional<ImageFile> result = service.importIfPresent(
                USER_PUBLIC_ID,
                kakaoUser
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(
                imageStorage,
                imageFileRepository
        );
    }

    @DisplayName("카카오 프로필 사진을 검증해 S3와 이미지 메타데이터에 저장한다")
    @Test
    void importsKakaoProfileImage() {
        byte[] jpegContent = {
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF,
                0x00
        };
        server.expect(requestTo(
                "https://example.com/profile.jpg"
        )).andRespond(withSuccess(
                jpegContent,
                MediaType.IMAGE_JPEG
        ));
        when(imageFileRepository.save(any(ImageFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<ImageFile> result = service.importIfPresent(
                USER_PUBLIC_ID,
                kakaoUser(
                        "https://example.com/profile.jpg",
                        false
                )
        );

        assertThat(result).isPresent();
        ImageFile savedImage = result.orElseThrow();
        assertThat(savedImage.getImagePurpose())
                .isEqualTo(ImagePurpose.PROFILE);
        assertThat(savedImage.getMimeType())
                .isEqualTo("image/jpeg");
        assertThat(savedImage.getSizeBytes())
                .isEqualTo(jpegContent.length);

        ArgumentCaptor<String> keyCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(imageStorage).put(
                keyCaptor.capture(),
                any(byte[].class),
                any(String.class)
        );
        String imageKey = keyCaptor.getValue();
        assertThat(imageKey)
                .startsWith("profiles/" + USER_PUBLIC_ID + "/")
                .endsWith(".jpg");
        assertThat(savedImage.getImageKey()).isEqualTo(imageKey);

        verify(imageFileRepository).save(savedImage);
        server.verify();
    }

    @DisplayName("카카오 이미지 다운로드 실패 시 이미지 없이 계속한다")
    @Test
    void fallsBackWhenDownloadFails() {
        server.expect(requestTo(
                "https://example.com/profile.jpg"
        )).andRespond(withServerError());

        Optional<ImageFile> result = service.importIfPresent(
                USER_PUBLIC_ID,
                kakaoUser(
                        "https://example.com/profile.jpg",
                        false
                )
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(
                imageStorage,
                imageFileRepository
        );
        server.verify();
    }

    @DisplayName("카카오 이미지가 제한 크기를 초과하면 저장하지 않는다")
    @Test
    void rejectsOversizedDownload() {
        byte[] jpegContent = {
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF,
                0x00
        };
        when(imageProperties.maxProfileBytes()).thenReturn(3);
        server.expect(requestTo(
                "https://example.com/profile.jpg"
        )).andRespond(withSuccess(
                jpegContent,
                MediaType.IMAGE_JPEG
        ));

        Optional<ImageFile> result = service.importIfPresent(
                USER_PUBLIC_ID,
                kakaoUser(
                        "https://example.com/profile.jpg",
                        false
                )
        );

        assertThat(result).isEmpty();
        verifyNoInteractions(imageStorage, imageFileRepository);
        server.verify();
    }

    @DisplayName("S3 저장 실패 시 이미지 없이 계속한다")
    @Test
    void fallsBackWhenStorageFails() {
        byte[] jpegContent = {
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF,
                0x00
        };
        server.expect(requestTo(
                "https://example.com/profile.jpg"
        )).andRespond(withSuccess(
                jpegContent,
                MediaType.IMAGE_JPEG
        ));
        doThrow(new RuntimeException("S3 저장 실패"))
                .when(imageStorage)
                .put(
                        any(String.class),
                        any(byte[].class),
                        any(String.class)
                );

        Optional<ImageFile> result = service.importIfPresent(
                USER_PUBLIC_ID,
                kakaoUser(
                        "https://example.com/profile.jpg",
                        false
                )
        );

        assertThat(result).isEmpty();
        verify(imageFileRepository, never())
                .save(any(ImageFile.class));
        server.verify();
    }

    @DisplayName("DB 저장 실패 시 S3 객체를 보상 삭제하고 이미지 없이 계속한다")
    @Test
    void compensatesStorageWhenMetadataSaveFails() {
        byte[] jpegContent = {
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF,
                0x00
        };
        server.expect(requestTo(
                "https://example.com/profile.jpg"
        )).andRespond(withSuccess(
                jpegContent,
                MediaType.IMAGE_JPEG
        ));
        when(imageFileRepository.save(any(ImageFile.class)))
                .thenThrow(new RuntimeException("DB 저장 실패"));

        Optional<ImageFile> result = service.importIfPresent(
                USER_PUBLIC_ID,
                kakaoUser(
                        "https://example.com/profile.jpg",
                        false
                )
        );

        assertThat(result).isEmpty();
        ArgumentCaptor<String> keyCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(imageStorage).delete(keyCaptor.capture());
        assertThat(keyCaptor.getValue())
                .startsWith("profiles/" + USER_PUBLIC_ID + "/")
                .endsWith(".jpg");
        server.verify();
    }

    @DisplayName("보상 삭제 실패도 로그인 흐름으로 전파하지 않는다")
    @Test
    void ignoresCompensationDeleteFailure() {
        byte[] jpegContent = {
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF,
                0x00
        };
        server.expect(requestTo(
                "https://example.com/profile.jpg"
        )).andRespond(withSuccess(
                jpegContent,
                MediaType.IMAGE_JPEG
        ));
        when(imageFileRepository.save(any(ImageFile.class)))
                .thenThrow(new RuntimeException("DB 저장 실패"));
        doThrow(new RuntimeException("S3 삭제 실패"))
                .when(imageStorage)
                .delete(any(String.class));

        Optional<ImageFile> result = service.importIfPresent(
                USER_PUBLIC_ID,
                kakaoUser(
                        "https://example.com/profile.jpg",
                        false
                )
        );

        assertThat(result).isEmpty();
        verify(imageStorage).delete(any(String.class));
        server.verify();
    }

    @DisplayName("상위 로그인 트랜잭션이 롤백되면 S3 객체를 보상 삭제한다")
    @Test
    void compensatesStorageWhenOuterTransactionRollsBack() {
        byte[] jpegContent = {
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF,
                0x00
        };
        server.expect(requestTo(
                "https://example.com/profile.jpg"
        )).andRespond(withSuccess(
                jpegContent,
                MediaType.IMAGE_JPEG
        ));
        when(imageFileRepository.save(any(ImageFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        try {
            Optional<ImageFile> result = service.importIfPresent(
                    USER_PUBLIC_ID,
                    kakaoUser(
                            "https://example.com/profile.jpg",
                            false
                    )
            );

            assertThat(result).isPresent();
            assertThat(TransactionSynchronizationManager
                    .getSynchronizations()).hasSize(1);
            TransactionSynchronizationManager
                    .getSynchronizations()
                    .forEach(synchronization ->
                            synchronization.afterCompletion(
                                    TransactionSynchronization.STATUS_ROLLED_BACK
                            ));

            verify(imageStorage).delete(
                    result.orElseThrow().getImageKey()
            );
            server.verify();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private KakaoUserResponse kakaoUser(
            String profileImageUrl,
            boolean defaultImage
    ) {
        return new KakaoUserResponse(
                123456789L,
                new KakaoUserResponse.KakaoAccount(
                        new KakaoUserResponse.Profile(
                                "플랜잇사용자",
                                profileImageUrl,
                                defaultImage
                        )
                )
        );
    }
}
