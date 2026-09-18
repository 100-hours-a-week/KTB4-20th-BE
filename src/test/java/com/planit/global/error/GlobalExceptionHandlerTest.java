package com.planit.global.error;

import com.planit.global.response.ApiResponse;
import com.planit.global.response.ValidationErrorReason;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void convertsBusinessExceptionUsingItsErrorCode() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.SERVICE_UNAVAILABLE)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getBody().message()).isEqualTo("서비스를 일시적으로 사용할 수 없습니다.");
        assertThat(response.getBody().data()).isNull();
    }

    @Test
    void convertsMissingRequestParameterToRequiredFieldError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingRequestParameter(
                new MissingServletRequestParameterException("cursor", "String")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INVALID_REQUEST");
        assertThat(response.getBody().errors()).hasSize(1);
        assertThat(response.getBody().errors().getFirst().field()).isEqualTo("cursor");
        assertThat(response.getBody().errors().getFirst().reason())
                .isEqualTo(ValidationErrorReason.REQUIRED);
    }

    @Test
    void hidesUnexpectedExceptionBehindInternalServerError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleUnexpectedException(
                new IllegalStateException("노출하면 안 되는 내부 상세")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().message()).doesNotContain("내부 상세");
    }
}
