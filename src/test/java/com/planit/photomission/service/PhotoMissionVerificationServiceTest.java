package com.planit.photomission.service;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.photomission.ai.PhotoMissionAiClient;
import com.planit.photomission.ai.PhotoMissionVerifyRequest;
import com.planit.photomission.ai.PhotoMissionVerifyResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhotoMissionVerificationServiceTest {

    private static final Clock SEOUL_CLOCK = Clock.fixed(
            Instant.parse("2026-10-17T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    private final PhotoMissionVerificationTransactionService transactionService =
            mock(PhotoMissionVerificationTransactionService.class);
    private final PhotoMissionAiClient aiClient =
            mock(PhotoMissionAiClient.class);
    private final PhotoMissionVerificationService service =
            new PhotoMissionVerificationService(
                    transactionService,
                    aiClient,
                    SEOUL_CLOCK
            );

    @DisplayName("트랜잭션 밖에서 AI를 호출하고 판정 결과를 반영한다")
    @Test
    void verifiesPhotoMission() {
        PhotoMissionVerifyRequest request = request();
        PhotoMissionVerificationTransactionService.PreparedVerification prepared =
                new PhotoMissionVerificationTransactionService.PreparedVerification(
                        51L,
                        31L,
                        request
                );
        PhotoMissionVerifyResponse response = successResponse();
        when(transactionService.prepare(41L)).thenReturn(prepared);
        when(aiClient.verify(31L, request)).thenReturn(response);

        PhotoMissionVerifyResponse result = service.verify(41L);

        assertThat(result).isSameAs(response);
        verify(transactionService).complete(51L, response);
    }

    @DisplayName("재시도 대기 판정을 새 AI 실행으로 이어간다")
    @Test
    void retriesPendingEvaluation() {
        PhotoMissionVerifyRequest request = request();
        LocalDateTime now = LocalDateTime.of(2026, 10, 17, 10, 0);
        PhotoMissionVerificationTransactionService.PreparedVerification prepared =
                new PhotoMissionVerificationTransactionService.PreparedVerification(
                        52L,
                        31L,
                        request
                );
        PhotoMissionVerifyResponse response = successResponse();
        when(transactionService.prepareRetry(51L, now)).thenReturn(prepared);
        when(aiClient.verify(31L, request)).thenReturn(response);

        service.retry(51L, now);

        verify(transactionService).complete(52L, response);
    }

    @DisplayName("재시도할 수 없어진 대기 판정은 실패로 종료한다")
    @Test
    void discardsUnavailablePendingEvaluation() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 17, 10, 0);
        PhotoMissionVerificationTransactionService.RetryUnavailableException
                exception =
                new PhotoMissionVerificationTransactionService
                        .RetryUnavailableException("이미 완료된 미션");
        when(transactionService.prepareRetry(51L, now))
                .thenThrow(exception);

        service.retry(51L, now);

        verify(transactionService).discardRetry(51L, now);
    }

    @DisplayName("재시도 준비 중 일시적 오류는 대기 판정을 종료하지 않는다")
    @Test
    void keepsPendingEvaluationAfterTransientPreparationFailure() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 17, 10, 0);
        RuntimeException exception = new RuntimeException("일시적 DB 오류");
        when(transactionService.prepareRetry(51L, now))
                .thenThrow(exception);

        assertThatThrownBy(() -> service.retry(51L, now))
                .isSameAs(exception);

        verify(transactionService, never()).discardRetry(51L, now);
    }

    @DisplayName("판정 결과 DB 반영 오류를 AI 실행 오류로 기록하지 않는다")
    @Test
    void doesNotRecordPersistenceFailureAsAiExecutionError() {
        PhotoMissionVerifyRequest request = request();
        PhotoMissionVerificationTransactionService.PreparedVerification prepared =
                new PhotoMissionVerificationTransactionService.PreparedVerification(
                        51L, 31L, request
                );
        PhotoMissionVerifyResponse response = successResponse();
        IllegalStateException exception =
                new IllegalStateException("판정 결과 반영 실패");
        when(transactionService.prepare(41L)).thenReturn(prepared);
        when(aiClient.verify(31L, request)).thenReturn(response);
        org.mockito.Mockito.doThrow(exception)
                .when(transactionService).complete(51L, response);

        assertThatThrownBy(() -> service.verify(41L))
                .isSameAs(exception);
        verify(transactionService, never())
                .waitForRetry(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(LocalDateTime.class)
                );
        verify(transactionService, never())
                .fail(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString()
                );
    }

    @DisplayName("네트워크 호출 오류는 재시도 대기로 기록하고 서비스 오류로 변환한다")
    @Test
    void recordsAiExecutionFailure() {
        PhotoMissionVerifyRequest request = request();
        PhotoMissionVerificationTransactionService.PreparedVerification prepared =
                new PhotoMissionVerificationTransactionService.PreparedVerification(
                        51L,
                        31L,
                        request
                );
        RestClientException exception =
                new RestClientException("AI 호출 실패");
        when(transactionService.prepare(41L)).thenReturn(prepared);
        when(aiClient.verify(31L, request)).thenThrow(exception);

        assertThatThrownBy(() -> service.verify(41L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        businessException -> {
                            assertThat(businessException.getErrorCode())
                                    .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
                            assertThat(businessException.getCause())
                                    .isSameAs(exception);
                        }
                );
        verify(transactionService).waitForRetry(
                org.mockito.ArgumentMatchers.eq(51L),
                org.mockito.ArgumentMatchers.eq("RestClientException"),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        );
    }

    @DisplayName("AI 429 응답은 Retry-After를 반영해 재시도 대기로 기록한다")
    @Test
    void recordsRateLimitAsRetryPending() {
        PhotoMissionVerifyRequest request = request();
        PhotoMissionVerificationTransactionService.PreparedVerification prepared =
                new PhotoMissionVerificationTransactionService.PreparedVerification(
                        51L, 31L, request
                );
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "30");
        RestClientResponseException exception = new RestClientResponseException(
                "호출 한도 초과",
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                headers,
                new byte[0],
                StandardCharsets.UTF_8
        );
        when(transactionService.prepare(41L)).thenReturn(prepared);
        when(aiClient.verify(31L, request)).thenThrow(exception);

        assertThatThrownBy(() -> service.verify(41L))
                .isInstanceOf(BusinessException.class);
        verify(transactionService).waitForRetry(
                org.mockito.ArgumentMatchers.eq(51L),
                org.mockito.ArgumentMatchers.eq("HTTP_429"),
                org.mockito.ArgumentMatchers.eq(
                        LocalDateTime.of(2026, 10, 17, 10, 0, 30)
                )
        );
    }

    @DisplayName("AI 401·422 응답은 재시도하지 않는 실행 실패로 기록한다")
    @ParameterizedTest
    @ValueSource(ints = {401, 422})
    void recordsPermanentAiErrorsAsExecutionFailure(int statusCode) {
        PhotoMissionVerifyRequest request = request();
        PhotoMissionVerificationTransactionService.PreparedVerification prepared =
                new PhotoMissionVerificationTransactionService.PreparedVerification(
                        51L, 31L, request
                );
        RestClientResponseException exception = new RestClientResponseException(
                "요청 형식 오류",
                statusCode,
                HttpStatus.valueOf(statusCode).getReasonPhrase(),
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
        when(transactionService.prepare(41L)).thenReturn(prepared);
        when(aiClient.verify(31L, request)).thenThrow(exception);

        assertThatThrownBy(() -> service.verify(41L))
                .isInstanceOf(BusinessException.class);
        verify(transactionService).fail(51L, "HTTP_" + statusCode);
    }

    @DisplayName("AI 502·504 응답은 재시도 대기로 기록한다")
    @ParameterizedTest
    @ValueSource(ints = {502, 504})
    void recordsTemporaryAiErrorsAsRetryPending(int statusCode) {
        PhotoMissionVerifyRequest request = request();
        PhotoMissionVerificationTransactionService.PreparedVerification prepared =
                new PhotoMissionVerificationTransactionService.PreparedVerification(
                        51L, 31L, request
                );
        RestClientResponseException exception = new RestClientResponseException(
                "AI 일시 오류",
                statusCode,
                HttpStatus.valueOf(statusCode).getReasonPhrase(),
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );
        when(transactionService.prepare(41L)).thenReturn(prepared);
        when(aiClient.verify(31L, request)).thenThrow(exception);

        assertThatThrownBy(() -> service.verify(41L))
                .isInstanceOf(BusinessException.class);
        verify(transactionService).waitForRetry(
                org.mockito.ArgumentMatchers.eq(51L),
                org.mockito.ArgumentMatchers.eq("HTTP_" + statusCode),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        );
    }

    private PhotoMissionVerifyRequest request() {
        return new PhotoMissionVerifyRequest(
                "https://bucket.example/photo.jpg",
                "place_abc",
                "첨성대",
                new PhotoMissionVerifyRequest.Coordinates(
                        BigDecimal.valueOf(35.8347),
                        BigDecimal.valueOf(129.2190)
                ),
                "첨성대 돌 가까이 찍기",
                null
        );
    }

    private PhotoMissionVerifyResponse successResponse() {
        return new PhotoMissionVerifyResponse(
                "success",
                null,
                BigDecimal.valueOf(95),
                List.of(),
                BigDecimal.valueOf(95),
                null
        );
    }
}
