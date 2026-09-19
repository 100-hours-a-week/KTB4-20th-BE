package com.planit.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Objects;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<ApiFieldError> errors
) {

    public ApiResponse {
        Objects.requireNonNull(code, "응답 코드는 필수입니다.");
        Objects.requireNonNull(message, "응답 메시지는 필수입니다.");
        errors = errors == null ? null : List.copyOf(errors);
    }

    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return new ApiResponse<>(code, message, data, null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(code, message, null, null);
    }

    public static ApiResponse<Void> validationError(
            String code,
            String message,
            List<ApiFieldError> errors
    ) {
        Objects.requireNonNull(errors, "검증 오류 목록은 필수입니다.");
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("검증 오류 목록은 비어 있을 수 없습니다.");
        }

        return new ApiResponse<>(code, message, null, errors);
    }
}
