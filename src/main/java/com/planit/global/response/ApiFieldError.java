package com.planit.global.response;

import java.util.Objects;

public record ApiFieldError(
        String field,
        ValidationErrorReason reason
) {

    public ApiFieldError {
        Objects.requireNonNull(field, "오류 필드는 필수입니다.");
        Objects.requireNonNull(reason, "검증 오류 사유는 필수입니다.");
    }
}
