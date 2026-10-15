package com.planit.photomission.service;

import com.planit.photomission.ai.PhotoMissionAiClient;
import com.planit.photomission.ai.PhotoMissionGenerateRequest;
import com.planit.photomission.ai.PhotoMissionGenerateResponse;
import com.planit.repository.TripRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhotoMissionGenerationServiceTest {

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final PhotoMissionGenerationTransactionService transactionService =
            mock(PhotoMissionGenerationTransactionService.class);
    private final PhotoMissionAiClient aiClient = mock(PhotoMissionAiClient.class);
    private final PhotoMissionGenerationService service =
            new PhotoMissionGenerationService(
                    tripRepository,
                    transactionService,
                    aiClient
            );

    @DisplayName("생성 대상 여행의 전체 일정을 AI에 요청하고 결과를 저장한다")
    @Test
    void generatesDueTrips() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 14, 0, 0);
        PhotoMissionGenerateRequest request =
                new PhotoMissionGenerateRequest("trip_1", List.of());
        PhotoMissionGenerateResponse response =
                new PhotoMissionGenerateResponse(List.of());
        when(tripRepository.findPhotoMissionGenerationTargetIds(
                now.toLocalDate(),
                now.minusMinutes(5)
        )).thenReturn(List.of(1L));
        when(transactionService.start(1L, now)).thenReturn(10L);
        when(transactionService.loadRequest(10L)).thenReturn(request);
        when(aiClient.generate(request)).thenReturn(response);

        service.generateDueTrips(now);

        verify(transactionService).complete(10L, response, now);
    }

    @DisplayName("AI 생성 호출이 실패하면 생성 작업을 실패 상태로 변경한다")
    @Test
    void marksJobFailedWhenAiCallFails() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 14, 0, 0);
        PhotoMissionGenerateRequest request =
                new PhotoMissionGenerateRequest("trip_1", List.of());
        when(tripRepository.findPhotoMissionGenerationTargetIds(
                now.toLocalDate(),
                now.minusMinutes(5)
        )).thenReturn(List.of(1L));
        when(transactionService.start(1L, now)).thenReturn(10L);
        when(transactionService.loadRequest(10L)).thenReturn(request);
        when(aiClient.generate(request))
                .thenThrow(new RestClientException("AI unavailable"));

        service.generateDueTrips(now);

        verify(transactionService).fail(10L);
    }

    @DisplayName("여행 시작일 00시 30분부터는 미션 생성을 시도하지 않는다")
    @Test
    void stopsGenerationAtPublicationTime() {
        LocalDateTime publicationTime =
                LocalDateTime.of(2026, 10, 14, 0, 30);

        service.generateDueTrips(publicationTime);

        verifyNoInteractions(
                tripRepository,
                transactionService,
                aiClient
        );
    }
}
