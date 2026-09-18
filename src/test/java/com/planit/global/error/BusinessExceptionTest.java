package com.planit.global.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void usesErrorCodeAndItsKoreanMessage() {
        BusinessException exception = new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("요청한 리소스를 찾을 수 없습니다.");
    }
}
