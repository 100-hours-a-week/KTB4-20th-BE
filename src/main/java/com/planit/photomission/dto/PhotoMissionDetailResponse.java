package com.planit.photomission.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PhotoMissionDetailResponse(
        Long missionId,
        int dayNumber,
        LocalDate date,
        String scope,
        String description,
        String primaryCategory,
        boolean expired,
        String status,
        int retryCount,
        String completionMethod,
        LocalDateTime completedAt,
        List<MemberStatus> members,
        List<Photo> photos
) {
    public record MemberStatus(
            Long tripMemberId,
            UUID userId,
            String username,
            String profileImageUrl,
            boolean me,
            String status,
            LocalDateTime completedAt
    ) {
    }

    public record Photo(
            Long photoId,
            String imageUrl,
            UUID submittedByUserId,
            String submittedByUsername,
            boolean representative,
            LocalDateTime submittedAt
    ) {
    }
}
