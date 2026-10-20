package com.planit.image.service;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageValidatorTest {

    private final ImageValidator imageValidator = new ImageValidator();

    @DisplayName("JPEG 파일의 실제 MIME 타입을 반환한다")
    @Test
    void validatesJpegSignature() {
        byte[] content = {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00
        };

        String mimeType = imageValidator.validate(
                content,
                "image/jpeg",
                10
        );

        assertThat(mimeType).isEqualTo("image/jpeg");
    }

    @DisplayName("PNG 파일의 실제 MIME 타입을 반환한다")
    @Test
    void validatesPngSignature() {
        byte[] content = {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };

        String mimeType = imageValidator.validate(
                content,
                "image/png",
                10
        );

        assertThat(mimeType).isEqualTo("image/png");
    }

    @DisplayName("WebP 파일의 실제 MIME 타입을 반환한다")
    @Test
    void validatesWebpSignature() {
        byte[] content = {
                'R', 'I', 'F', 'F', 0x00, 0x00, 0x00, 0x00,
                'W', 'E', 'B', 'P'
        };

        String mimeType = imageValidator.validate(
                content,
                "image/webp",
                12
        );

        assertThat(mimeType).isEqualTo("image/webp");
    }

    @DisplayName("Content-Type의 대소문자와 파라미터를 정규화한다")
    @Test
    void normalizesDeclaredContentType() {
        byte[] content = {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00
        };

        String mimeType = imageValidator.validate(
                content,
                "IMAGE/JPEG; charset=binary",
                10
        );

        assertThat(mimeType).isEqualTo("image/jpeg");
    }

    @DisplayName("빈 이미지 파일을 거부한다")
    @Test
    void rejectsEmptyContent() {
        assertBusinessError(
                () -> imageValidator.validate(
                        new byte[0],
                        "image/jpeg",
                        10
                ),
                ErrorCode.INVALID_REQUEST
        );
    }

    @DisplayName("실제 다운로드한 파일이 최대 크기를 초과하면 거부한다")
    @Test
    void rejectsActualContentLargerThanLimit() {
        byte[] content = {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00
        };

        assertBusinessError(
                () -> imageValidator.validate(
                        content,
                        "image/jpeg",
                        3
                ),
                ErrorCode.FILE_TOO_LARGE
        );
    }

    @DisplayName("허용하지 않는 선언 MIME 타입을 거부한다")
    @Test
    void rejectsUnsupportedDeclaredContentType() {
        byte[] content = {
                'G', 'I', 'F', '8', '9', 'a'
        };

        assertBusinessError(
                () -> imageValidator.validate(
                        content,
                        "image/gif",
                        10
                ),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE
        );
    }

    @DisplayName("선언 MIME 타입과 실제 파일 시그니처가 다르면 거부한다")
    @Test
    void rejectsMimeTypeAndSignatureMismatch() {
        byte[] pngContent = {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };

        assertBusinessError(
                () -> imageValidator.validate(
                        pngContent,
                        "image/jpeg",
                        10
                ),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE
        );
    }

    @DisplayName("알 수 없는 파일 시그니처를 거부한다")
    @Test
    void rejectsUnknownSignature() {
        byte[] content = {
                '<', 'h', 't', 'm', 'l', '>'
        };

        assertBusinessError(
                () -> imageValidator.validate(
                        content,
                        "image/jpeg",
                        10
                ),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE
        );
    }

    private void assertBusinessError(
            Runnable action,
            ErrorCode expectedErrorCode
    ) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(
                                exception.getErrorCode()
                        ).isEqualTo(expectedErrorCode)
                );
    }
}
