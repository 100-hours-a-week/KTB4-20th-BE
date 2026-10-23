package com.planit.photomission.service;

import com.planit.photomission.ai.PhotoMissionAiClient;
import com.planit.photomission.ai.PhotoMissionGenerateRequest;
import com.planit.photomission.ai.PhotoMissionGenerateResponse;
import com.planit.repository.TripRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhotoMissionGenerationServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 10, 14, 14, 10);

    private final TripRepository tripRepository = mock(TripRepository.class);
    private final PhotoMissionGenerationTransactionService transactionService =
            mock(PhotoMissionGenerationTransactionService.class);
    private final PhotoMissionAiClient aiClient = mock(PhotoMissionAiClient.class);
    private final PhotoMissionGenerationService service =
            new PhotoMissionGenerationService(
                    tripRepository,
                    transactionService,
                    aiClient,
                    Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL)
            );

    @DisplayName("생성 대상 여행의 전체 일정을 AI에 요청하고 결과를 저장한다")
    @Test
    void generatesDueTrips() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 14, 14, 10);
        PhotoMissionGenerateRequest request =
                new PhotoMissionGenerateRequest("trip_1", List.of());
        PhotoMissionGenerateResponse response =
                new PhotoMissionGenerateResponse(List.of());
        when(tripRepository.findPhotoMissionGenerationTargetIds(
                now.toLocalDate(),
                now.minusMinutes(5)
        )).thenReturn(List.of(1L));
        MissionGenerationAttempt attempt =
                new MissionGenerationAttempt(10L, 1);
        when(transactionService.start(1L, now)).thenReturn(attempt);
        when(transactionService.loadRequest(10L)).thenReturn(request);
        when(aiClient.generate(request)).thenReturn(response);

        service.generateDueTrips(now);

        verify(transactionService).complete(attempt, response, now);
    }

    @DisplayName("AI 생성 호출이 실패하면 생성 작업을 실패 상태로 변경한다")
    @Test
    void marksJobFailedWhenAiCallFails() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 14, 14, 10);
        PhotoMissionGenerateRequest request =
                new PhotoMissionGenerateRequest("trip_1", List.of());
        when(tripRepository.findPhotoMissionGenerationTargetIds(
                now.toLocalDate(),
                now.minusMinutes(5)
        )).thenReturn(List.of(1L));
        MissionGenerationAttempt attempt =
                new MissionGenerationAttempt(10L, 1);
        when(transactionService.start(1L, now)).thenReturn(attempt);
        when(transactionService.loadRequest(10L)).thenReturn(request);
        when(aiClient.generate(request))
                .thenThrow(new RestClientException("AI unavailable"));

        service.generateDueTrips(now);

        verify(transactionService).fail(attempt);
    }

    @DisplayName("실패 상태 저장이 실패해도 다음 여행 생성을 계속한다")
    @Test
    void continuesWhenMarkingJobFailedAlsoFails() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 14, 14, 10);
        PhotoMissionGenerateRequest firstRequest =
                new PhotoMissionGenerateRequest("trip_1", List.of());
        PhotoMissionGenerateRequest secondRequest =
                new PhotoMissionGenerateRequest("trip_2", List.of());
        PhotoMissionGenerateResponse secondResponse =
                new PhotoMissionGenerateResponse(List.of());
        MissionGenerationAttempt firstAttempt =
                new MissionGenerationAttempt(10L, 1);
        MissionGenerationAttempt secondAttempt =
                new MissionGenerationAttempt(20L, 1);
        when(tripRepository.findPhotoMissionGenerationTargetIds(
                now.toLocalDate(),
                now.minusMinutes(5)
        )).thenReturn(List.of(1L, 2L));
        when(transactionService.start(1L, now)).thenReturn(firstAttempt);
        when(transactionService.loadRequest(10L)).thenReturn(firstRequest);
        when(aiClient.generate(firstRequest))
                .thenThrow(new RestClientException("AI unavailable"));
        doThrow(new IllegalStateException("DB unavailable"))
                .when(transactionService).fail(firstAttempt);
        when(transactionService.start(2L, now)).thenReturn(secondAttempt);
        when(transactionService.loadRequest(20L)).thenReturn(secondRequest);
        when(aiClient.generate(secondRequest)).thenReturn(secondResponse);

        service.generateDueTrips(now);

        verify(transactionService).complete(
                secondAttempt,
                secondResponse,
                now
        );
    }

    @DisplayName("여행 시작일 14시 30분부터는 미션 생성을 시도하지 않는다")
    @Test
    void stopsGenerationAtPublicationTime() {
        LocalDateTime publicationTime =
                LocalDateTime.of(2026, 10, 14, 14, 30);

        service.generateDueTrips(publicationTime);

        verifyNoInteractions(
                tripRepository,
                transactionService,
                aiClient
        );
    }

    @DisplayName("각 여행은 실제 처리를 시작한 시각으로 생성 작업을 기록한다")
    @Test
    void usesCurrentTimeForEachTrip() {
        MutableClock clock = new MutableClock(NOW, SEOUL);
        PhotoMissionGenerationService generationService =
                new PhotoMissionGenerationService(
                        tripRepository,
                        transactionService,
                        aiClient,
                        clock
                );
        PhotoMissionGenerateRequest firstRequest =
                new PhotoMissionGenerateRequest("trip_1", List.of());
        PhotoMissionGenerateRequest secondRequest =
                new PhotoMissionGenerateRequest("trip_2", List.of());
        PhotoMissionGenerateResponse response =
                new PhotoMissionGenerateResponse(List.of());
        MissionGenerationAttempt firstAttempt =
                new MissionGenerationAttempt(10L, 1);
        MissionGenerationAttempt secondAttempt =
                new MissionGenerationAttempt(20L, 1);
        when(tripRepository.findPhotoMissionGenerationTargetIds(
                NOW.toLocalDate(),
                NOW.minusMinutes(5)
        )).thenReturn(List.of(1L, 2L));
        when(transactionService.start(1L, NOW)).thenReturn(firstAttempt);
        when(transactionService.loadRequest(10L)).thenReturn(firstRequest);
        when(aiClient.generate(firstRequest)).thenAnswer(invocation -> {
            clock.advance(Duration.ofMinutes(1));
            return response;
        });
        LocalDateTime secondStart = NOW.plusMinutes(1);
        when(transactionService.start(2L, secondStart))
                .thenReturn(secondAttempt);
        when(transactionService.loadRequest(20L)).thenReturn(secondRequest);
        when(aiClient.generate(secondRequest)).thenReturn(response);

        generationService.generateDueTrips(NOW);

        verify(transactionService).complete(
                firstAttempt,
                response,
                secondStart
        );
        verify(transactionService).start(2L, secondStart);
    }

    @DisplayName("처리 중 공개 시각이 되면 남은 여행 생성을 시작하지 않는다")
    @Test
    void stopsRemainingTripsAtPublicationTime() {
        LocalDateTime beforePublication =
                LocalDateTime.of(2026, 10, 14, 14, 29);
        MutableClock clock = new MutableClock(beforePublication, SEOUL);
        PhotoMissionGenerationService generationService =
                new PhotoMissionGenerationService(
                        tripRepository,
                        transactionService,
                        aiClient,
                        clock
                );
        PhotoMissionGenerateRequest request =
                new PhotoMissionGenerateRequest("trip_1", List.of());
        PhotoMissionGenerateResponse response =
                new PhotoMissionGenerateResponse(List.of());
        MissionGenerationAttempt attempt =
                new MissionGenerationAttempt(10L, 1);
        when(tripRepository.findPhotoMissionGenerationTargetIds(
                beforePublication.toLocalDate(),
                beforePublication.minusMinutes(5)
        )).thenReturn(List.of(1L, 2L));
        when(transactionService.start(1L, beforePublication))
                .thenReturn(attempt);
        when(transactionService.loadRequest(10L)).thenReturn(request);
        when(aiClient.generate(request)).thenAnswer(invocation -> {
            clock.advance(Duration.ofMinutes(1));
            return response;
        });

        generationService.generateDueTrips(beforePublication);

        verify(transactionService, never()).start(eq(2L), any());
    }

    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        private MutableClock(LocalDateTime initialTime, ZoneId zone) {
            this.instant = initialTime.atZone(zone).toInstant();
            this.zone = zone;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new MutableClock(
                    LocalDateTime.ofInstant(instant, newZone),
                    newZone
            );
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
