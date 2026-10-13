package com.planit.photomission.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record PhotoMissionVerifyRequest(
        @JsonProperty("image_url")
        String imageUrl,
        @JsonProperty("place_id")
        String placeId,
        @JsonProperty("place_name")
        String placeName,
        @JsonProperty("place_coordinates")
        Coordinates placeCoordinates,
        @JsonProperty("mission_description")
        String missionDescription,
        @JsonProperty("photo_coordinates")
        Coordinates photoCoordinates
) {

    public record Coordinates(
            BigDecimal latitude,
            BigDecimal longitude
    ) {
    }
}
