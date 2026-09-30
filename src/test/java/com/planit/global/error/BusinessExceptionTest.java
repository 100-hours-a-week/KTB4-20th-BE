package com.planit.global.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @DisplayName("비즈니스 예외가 오류 코드와 한국어 메시지를 사용한다")
    @Test
    void usesErrorCodeAndItsKoreanMessage() {
        BusinessException exception = new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("요청한 리소스를 찾을 수 없습니다.");
    }
}
