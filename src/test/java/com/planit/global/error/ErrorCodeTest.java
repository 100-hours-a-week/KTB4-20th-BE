package com.planit.global.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void exposesStableCodeStatusAndKoreanMessage() {
        ErrorCode errorCode = ErrorCode.AUTHENTICATION_REQUIRED;

        assertThat(errorCode.getCode()).isEqualTo("AUTHENTICATION_REQUIRED");
        assertThat(errorCode.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(errorCode.getMessage()).isEqualTo("인증이 필요합니다.");
    }
}
