package com.planit.photomission.service;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.photomission.ai.PhotoMissionAiClient;
import com.planit.photomission.ai.PhotoMissionVerifyRequest;
import com.planit.photomission.ai.PhotoMissionVerifyResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhotoMissionVerificationServiceTest {

    private final PhotoMissionVerificationTransactionService transactionService =
            mock(PhotoMissionVerificationTransactionService.class);
    private final PhotoMissionAiClient aiClient =
            mock(PhotoMissionAiClient.class);
    private final PhotoMissionVerificationService service =
            new PhotoMissionVerificationService(transactionService, aiClient);

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

    @DisplayName("AI 호출 오류는 실행 실패로 기록하고 서비스 오류로 변환한다")
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
        verify(transactionService).fail(
                51L,
                "RestClientException"
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
