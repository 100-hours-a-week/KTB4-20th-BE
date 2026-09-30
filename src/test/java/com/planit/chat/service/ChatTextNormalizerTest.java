package com.planit.chat.service;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatTextNormalizerTest {

    private final ChatTextNormalizer normalizer = new ChatTextNormalizer();

    @DisplayName("가로 공백을 정규화하고 줄바꿈은 유지한다")
    @Test
    void normalizesHorizontalWhitespaceAndPreservesLineBreaks() {
        String normalized = normalizer.normalize("  경주\t 맛집  \n두 번째 줄  ");

        assertThat(normalized).isEqualTo("경주 맛집 \n두 번째 줄");
    }

    @DisplayName("공백으로만 구성된 메시지를 거부한다")
    @Test
    void rejectsBlankText() {
        assertThatThrownBy(() -> normalizer.normalize(" \t\n "))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CHAT_MESSAGE_PAYLOAD);
    }

    @DisplayName("메시지 길이를 유니코드 문자 수로 계산한다")
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
