package com.planit.photomission.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PhotoMissionAlbumResponse(
        Long tripId,
        String tripName,
        int completedMissionCount,
        int totalPhotoCount,
        List<DayAlbum> days
) {
    public record DayAlbum(
            int dayNumber,
            LocalDate date,
            List<MissionAlbum> personalMissions,
            List<MissionAlbum> groupMissions
    ) {
    }

    public record MissionAlbum(
            Long missionId,
            String scope,
            String description,
            String primaryCategory,
            String thumbnailUrl,
            int photoCount,
            SubmittedBy submittedBy
    ) {
    }

    public record SubmittedBy(
            UUID userId,
            String username
    ) {
    }
}
