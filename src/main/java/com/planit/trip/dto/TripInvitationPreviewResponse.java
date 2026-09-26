package com.planit.trip.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TripInvitationPreviewResponse(
        Trip trip,
        Inviter inviter,
        List<Member> members,
        boolean alreadyJoined,
        ConflictingTrip conflictingTrip
) {

    public TripInvitationPreviewResponse {
        members = List.copyOf(members);
    }

    public record Trip(
            String tripId,
            String name,
            Region region,
            LocalDate startDate,
            LocalDate endDate,
            int memberCount,
            int capacity
    ) {
    }

    public record Region(
            String regionId,
            String regionName
    ) {
    }

    public record Inviter(
            UUID publicId,
            String userName,
            String profileImageUrl
    ) {
    }

    public record Member(
            String userName,
            String profileImageUrl
    ) {
    }

    public record ConflictingTrip(
            String tripId,
            String name
    ) {
    }
}
