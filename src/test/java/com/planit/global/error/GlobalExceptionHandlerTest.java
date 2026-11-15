package com.planit.global.error;

import com.planit.global.response.ApiResponse;
import com.planit.global.response.ValidationErrorReason;
import com.planit.mq.intake.AiJobTemporarilyUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @DisplayName("비즈니스 예외를 해당 오류 코드의 응답으로 변환한다")
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

    @DisplayName("큐 차단으로 접수하지 않은 요청은 503과 재시도 대기 시간(초)을 Retry-After 헤더로 응답한다")
    @Test
    void addsRetryAfterHeaderWhenAiJobIsTemporarilyUnavailable() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleAiJobTemporarilyUnavailable(
                new AiJobTemporarilyUnavailableException(Duration.ofSeconds(60))
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("AI_JOB_TEMPORARILY_UNAVAILABLE");
        assertThat(response.getBody().message()).isEqualTo("요청이 많아 잠시 후 다시 시도해 주세요.");
        assertThat(response.getBody().data()).isNull();
    }

    @DisplayName("진행 중인 AI 작업이 있으면 409로 응답한다")
    @Test
    void convertsAiJobAlreadyInProgressToConflict() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.AI_JOB_ALREADY_IN_PROGRESS)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("AI_JOB_ALREADY_IN_PROGRESS");
        assertThat(response.getBody().message()).isEqualTo("이미 진행 중인 AI 작업이 있습니다.");
    }

    @DisplayName("필수 요청 파라미터 누락을 필수값 오류로 변환한다")
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

    @DisplayName("예상하지 못한 예외의 상세 내용을 숨기고 서버 오류로 응답한다")
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
