package com.planit.global.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @DisplayName("오류 코드가 고정된 코드와 상태, 한국어 메시지를 제공한다")
    @Test
    void exposesStableCodeStatusAndKoreanMessage() {
        ErrorCode errorCode = ErrorCode.AUTHENTICATION_REQUIRED;

        assertThat(errorCode.getCode()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(errorCode.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorCode.getMessage()).isEqualTo("인증이 필요합니다.");
    }
}
