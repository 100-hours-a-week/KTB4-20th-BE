package com.planit.trip.dto;

import java.time.LocalDate;

public record TripConflictResponse(
        String tripId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        boolean canLeave
) {
}
