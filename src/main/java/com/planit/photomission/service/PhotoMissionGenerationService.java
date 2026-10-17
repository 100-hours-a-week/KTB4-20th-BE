package com.planit.photomission.service;

import com.planit.photomission.ai.PhotoMissionAiClient;
import com.planit.photomission.ai.PhotoMissionGenerateRequest;
import com.planit.photomission.ai.PhotoMissionGenerateResponse;
import com.planit.photomission.domain.MissionGenerationJob;
import com.planit.repository.TripRepository;
import io.sentry.Sentry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class PhotoMissionGenerationService {

    private static final LocalTime PUBLICATION_TIME = LocalTime.of(0, 30);

    private final TripRepository tripRepository;
    private final PhotoMissionGenerationTransactionService transactionService;
    private final PhotoMissionAiClient aiClient;
    private final Clock photoMissionClock;

    public PhotoMissionGenerationService(
            TripRepository tripRepository,
            PhotoMissionGenerationTransactionService transactionService,
            PhotoMissionAiClient aiClient,
            @Qualifier("photoMissionClock") Clock photoMissionClock
    ) {
        this.tripRepository = tripRepository;
        this.transactionService = transactionService;
        this.aiClient = aiClient;
        this.photoMissionClock = photoMissionClock;
    }

    public void generateDueTrips(LocalDateTime now) {
        if (!now.toLocalTime().isBefore(PUBLICATION_TIME)) {
            return;
        }
        for (Long tripId : tripRepository
                .findPhotoMissionGenerationTargetIds(
                        now.toLocalDate(),
                        MissionGenerationJob.staleBefore(now)
                )) {
            LocalDateTime attemptedAt = LocalDateTime.now(photoMissionClock);
            if (!attemptedAt.toLocalDate().equals(now.toLocalDate())
                    || !attemptedAt.toLocalTime().isBefore(PUBLICATION_TIME)) {
                break;
            }
            generate(tripId, attemptedAt);
        }
    }

    private void generate(Long tripId, LocalDateTime now) {
        MissionGenerationAttempt attempt = null;
        try {
            attempt = transactionService.start(tripId, now);
            PhotoMissionGenerateRequest request =
                    transactionService.loadRequest(attempt.jobId());
            PhotoMissionGenerateResponse response = aiClient.generate(request);
            transactionService.complete(
                    attempt,
                    response,
                    LocalDateTime.now(photoMissionClock)
            );
        } catch (RuntimeException exception) {
            Sentry.captureException(exception);
            markFailed(attempt);
        }
    }

    private void markFailed(MissionGenerationAttempt attempt) {
        if (attempt == null) {
            return;
        }
        try {
            transactionService.fail(attempt);
        } catch (RuntimeException exception) {
            Sentry.captureException(exception);
        }
    }
}
