package com.planit.trip.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TripJoinRequest(

        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_-]{43}$")
        String invitationToken
) {
}
