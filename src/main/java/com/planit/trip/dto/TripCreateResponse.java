package com.planit.trip.dto;

public record TripCreateResponse(
        String tripId,
        String invitationToken
) {
}
