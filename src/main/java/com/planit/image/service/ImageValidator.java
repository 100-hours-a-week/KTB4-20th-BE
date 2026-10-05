package com.planit.image.service;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ImageValidator {

    private static final String JPEG = "image/jpeg";
    private static final String PNG = "image/png";
    private static final String WEBP = "image/webp";
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            JPEG,
            PNG,
            WEBP
    );

    public String validate(
            byte[] content,
            String declaredContentType,
            int maxBytes
    ) {
        validateNotEmpty(content);
        validateSize(content, maxBytes);

        String declaredMimeType = normalizeContentType(
                declaredContentType
        );
        validateAllowedMimeType(declaredMimeType);

        String actualMimeType = detectMimeType(content);
        if (!declaredMimeType.equals(actualMimeType)) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE
            );
        }

        return actualMimeType;
    }

    private void validateNotEmpty(byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private void validateSize(byte[] content, int maxBytes) {
        if (maxBytes <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (content.length > maxBytes) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE
            );
        }

        return contentType
                .split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private void validateAllowedMimeType(String mimeType) {
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new BusinessException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE
            );
        }
    }

    private String detectMimeType(byte[] content) {
        if (isJpeg(content)) {
            return JPEG;
        }
        if (isPng(content)) {
            return PNG;
        }
        if (isWebp(content)) {
            return WEBP;
        }

        throw new BusinessException(
                ErrorCode.UNSUPPORTED_MEDIA_TYPE
        );
    }

    private boolean isJpeg(byte[] content) {
        return content.length >= 3
                && unsigned(content[0]) == 0xFF
                && unsigned(content[1]) == 0xD8
                && unsigned(content[2]) == 0xFF;
    }

    private boolean isPng(byte[] content) {
        int[] signature = {
                0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };
        if (content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (unsigned(content[index]) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean isWebp(byte[] content) {
        return content.length >= 12
                && content[0] == 'R'
                && content[1] == 'I'
                && content[2] == 'F'
                && content[3] == 'F'
                && content[8] == 'W'
                && content[9] == 'E'
                && content[10] == 'B'
                && content[11] == 'P';
    }

    private int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }
}
