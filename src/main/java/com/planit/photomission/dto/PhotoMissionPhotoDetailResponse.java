package com.planit.photomission.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PhotoMissionPhotoDetailResponse(
        Long photoId,
        String imageUrl,
        LocalDateTime submittedAt,
        boolean representative,
        MissionSummary mission,
        PlaceSummary place,
        SubmittedBy submittedBy
) {
    public record MissionSummary(
            Long missionId,
            String scope,
            String description,
            String primaryCategory
    ) {
    }

    public record PlaceSummary(
            String placeId,
            String placeName
    ) {
    }

    public record SubmittedBy(
            UUID userId,
            String username
    ) {
    }
}
