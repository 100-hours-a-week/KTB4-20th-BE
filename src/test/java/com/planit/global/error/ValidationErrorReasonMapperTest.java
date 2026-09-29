package com.planit.global.error;

import com.planit.global.response.ValidationErrorReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationErrorReasonMapperTest {

    @DisplayName("검증 제약 조건을 정의된 오류 사유로 변환한다")
    @ParameterizedTest
    @CsvSource({
            "NotBlank, REQUIRED",
            "Email, INVALID_FORMAT",
            "Size, INVALID_LENGTH",
            "Max, OUT_OF_RANGE",
            "CustomConstraint, INVALID_VALUE"
    })
    void mapsConstraintToApprovedReason(String constraint, ValidationErrorReason expected) {
        assertThat(ValidationErrorReasonMapper.from(constraint)).isEqualTo(expected);
    }
}
