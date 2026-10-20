package com.planit.photomission.dto;

import java.time.LocalDate;
import java.util.List;

public record PhotoMissionCompletionResponse(
        Long tripId,
        String tripName,
        LocalDate startDate,
        LocalDate endDate,
        String scope,
        Integer dayNumber,
        LocalDate date,
        int completedMissionCount,
        int totalMissionCount,
        int incompleteMissionCount,
        int totalPhotoCount,
        int landmarkCount,
        boolean completed,
        boolean tripEnded,
        boolean nextDayExists,
        List<PreviewPhoto> previewPhotos
) {
    public record PreviewPhoto(
            Long photoId,
            String imageUrl
    ) {
    }
}
