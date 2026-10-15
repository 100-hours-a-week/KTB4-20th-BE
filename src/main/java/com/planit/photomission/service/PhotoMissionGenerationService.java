package com.planit.photomission.service;

import com.planit.photomission.ai.PhotoMissionAiClient;
import com.planit.photomission.ai.PhotoMissionGenerateRequest;
import com.planit.photomission.ai.PhotoMissionGenerateResponse;
import com.planit.photomission.domain.MissionGenerationJob;
import com.planit.repository.TripRepository;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class PhotoMissionGenerationService {

    private static final LocalTime PUBLICATION_TIME = LocalTime.of(0, 30);

    private final TripRepository tripRepository;
    private final PhotoMissionGenerationTransactionService transactionService;
    private final PhotoMissionAiClient aiClient;

    public void generateDueTrips(LocalDateTime now) {
        if (!now.toLocalTime().isBefore(PUBLICATION_TIME)) {
            return;
        }
        for (Long tripId : tripRepository
                .findPhotoMissionGenerationTargetIds(
                        now.toLocalDate(),
                        MissionGenerationJob.staleBefore(now)
                )) {
            generate(tripId, now);
        }
    }

    private void generate(Long tripId, LocalDateTime now) {
        Long jobId = null;
        try {
            jobId = transactionService.start(tripId, now);
            PhotoMissionGenerateRequest request =
                    transactionService.loadRequest(jobId);
            PhotoMissionGenerateResponse response = aiClient.generate(request);
            transactionService.complete(jobId, response, now);
        } catch (RuntimeException exception) {
            if (jobId != null) {
                transactionService.fail(jobId);
            }
            Sentry.captureException(exception);
        }
    }
}
