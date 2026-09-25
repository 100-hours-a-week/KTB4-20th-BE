package com.planit.chat.service;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatTextNormalizerTest {

    private final ChatTextNormalizer normalizer = new ChatTextNormalizer();

    @Test
    void normalizesHorizontalWhitespaceAndPreservesLineBreaks() {
        String normalized = normalizer.normalize("  경주\t 맛집  \n두 번째 줄  ");

        assertThat(normalized).isEqualTo("경주 맛집 \n두 번째 줄");
    }

    @Test
    void rejectsBlankText() {
        assertThatThrownBy(() -> normalizer.normalize(" \t\n "))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CHAT_MESSAGE_PAYLOAD);
    }

    @Test
    void countsUnicodeCodePointsInsteadOfUtf16CodeUnits() {
        assertThat(normalizer.normalize("😀".repeat(1_000)))
                .hasSize(2_000);

        assertThatThrownBy(() -> normalizer.normalize("😀".repeat(1_001)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.CHAT_MESSAGE_TOO_LONG);
    }
}
