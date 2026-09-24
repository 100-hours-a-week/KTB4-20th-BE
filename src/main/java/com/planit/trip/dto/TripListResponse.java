package com.planit.trip.dto;

import java.time.LocalDate;
import java.util.List;

public record TripListResponse(
        List<TripSummary> trips,
        String nextCursor,
        boolean hasNext
) {
    public record TripSummary(
            String tripId,
            String name,
            LocalDate startDate,
            int memberCount,
            List<MemberSummary> members
    ) {
    }

    public record MemberSummary(
            String userName,
            String profileImageUrl
    ) {
    }
}
