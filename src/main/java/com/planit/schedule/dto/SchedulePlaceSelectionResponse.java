package com.planit.schedule.dto;

import com.planit.ai.AiPlaceSelectionResponse;

import java.util.List;

public record SchedulePlaceSelectionResponse(
        String tripId,
        List<AiPlaceSelectionResponse.Place> places
) {
    public SchedulePlaceSelectionResponse {
        places = List.copyOf(places);
    }
}
