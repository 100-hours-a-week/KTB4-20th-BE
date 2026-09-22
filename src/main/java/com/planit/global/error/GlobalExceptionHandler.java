package com.planit.global.error;

import com.planit.global.response.ApiFieldError;
import com.planit.global.response.ApiResponse;
import com.planit.global.response.ValidationErrorReason;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return errorResponse(exception.getErrorCode());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception
    ) {
        List<ApiFieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toApiFieldError)
                .distinct()
                .toList();

        return validationErrorResponse(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        List<ApiFieldError> errors = exception.getConstraintViolations().stream()
                .map(violation -> new ApiFieldError(
                        lastPathSegment(violation.getPropertyPath().toString()),
                        ValidationErrorReasonMapper.from(
                                violation.getConstraintDescriptor().getAnnotation()
                                        .annotationType().getSimpleName()
                        )
                ))
                .distinct()
                .toList();

        return validationErrorResponse(errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestParameter(
            MissingServletRequestParameterException exception
    ) {
        return validationErrorResponse(List.of(
                new ApiFieldError(exception.getParameterName(), ValidationErrorReason.REQUIRED)
        ));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestHeader(
            MissingRequestHeaderException exception
    ) {
        return validationErrorResponse(List.of(
                new ApiFieldError(exception.getHeaderName(), ValidationErrorReason.REQUIRED)
        ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        return validationErrorResponse(List.of(
                new ApiFieldError(exception.getName(), ValidationErrorReason.INVALID_VALUE)
        ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable() {
        return errorResponse(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed() {
        return errorResponse(ErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType() {
        return errorResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded() {
        return errorResponse(ErrorCode.FILE_TOO_LARGE);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound() {
        return errorResponse(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("처리되지 않은 서버 예외가 발생했습니다.", exception);
        return errorResponse(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    private ApiFieldError toApiFieldError(FieldError fieldError) {
        return new ApiFieldError(
                fieldError.getField(),
                ValidationErrorReasonMapper.from(fieldError.getCode())
        );
    }

    private ResponseEntity<ApiResponse<Void>> validationErrorResponse(List<ApiFieldError> errors) {
        ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.validationError(
                        errorCode.getCode(),
                        errorCode.getMessage(),
                        errors
                ));
    }

    private ResponseEntity<ApiResponse<Void>> errorResponse(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    private String lastPathSegment(String propertyPath) {
        int separatorIndex = propertyPath.lastIndexOf('.');
        return separatorIndex < 0 ? propertyPath : propertyPath.substring(separatorIndex + 1);
    }
}
