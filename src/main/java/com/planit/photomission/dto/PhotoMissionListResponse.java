package com.planit.photomission.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PhotoMissionListResponse(
        TripSummary trip,
        List<DayTab> days,
        SelectedDay selectedDay
) {
    public record TripSummary(
            Long tripId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            int memberCount
    ) {
    }

    public record DayTab(
            int dayNumber,
            LocalDate date,
            boolean selectable,
            boolean selected
    ) {
    }

    public record SelectedDay(
            int dayNumber,
            LocalDate date,
            boolean published,
            int completedMissionCount,
            int totalMissionCount,
            boolean completed,
            boolean nextDayExists,
            List<MissionCard> missions
    ) {
    }

    public record MissionCard(
            Long missionId,
            int missionOrder,
            String scope,
            String description,
            String primaryCategory,
            String status,
            String thumbnailUrl,
            Integer completedMemberCount,
            Integer totalMemberCount,
            CompletedBy completedBy
    ) {
    }

    public record CompletedBy(
            UUID userId,
            String username,
            LocalDateTime completedAt
    ) {
    }
}
