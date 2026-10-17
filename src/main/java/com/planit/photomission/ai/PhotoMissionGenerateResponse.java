package com.planit.photomission.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PhotoMissionGenerateResponse(
        List<Mission> missions
) {
    public record Mission(
            @JsonProperty("mission_id")
            String missionId,
            @JsonProperty("place_id")
            String placeId,
            String description,
            String scope,
            @JsonProperty("primary_category")
            String primaryCategory
    ) {
    }
}
