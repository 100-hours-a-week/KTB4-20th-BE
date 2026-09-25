package com.planit.trip.dto;

import com.planit.domain.TripMemberRole;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TripDetailResponse(
        String tripId,
        String name,
        Region region,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        int memberCount,
        TripMemberRole myRole,
        OffsetDateTime surveyDeadlineAt,
        OffsetDateTime createdAt,
        List<Member> members
) {
    public TripDetailResponse {
        members = List.copyOf(members);
    }

    public record Region(
            String regionId,
            String regionCode,
            String regionName
    ) {
    }

    public record Member(
            UUID userPublicId,
            String userName,
            String profileImageUrl,
            TripMemberRole role
    ) {
    }
}
