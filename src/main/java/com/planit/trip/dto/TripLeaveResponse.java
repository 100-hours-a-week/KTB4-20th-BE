package com.planit.trip.dto;

public record TripLeaveResponse(
        boolean tripDeleted,
        String newHostMemberId
) {
}
