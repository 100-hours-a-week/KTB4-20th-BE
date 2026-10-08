package com.planit.schedule.dto;

import com.planit.ai.AiPlaceSelectionResponse;

import java.util.List;

public record SchedulePlaceSelectionResponse(
        String tripId,
        List<List<AiPlaceSelectionResponse.Place>> places
) {
    public SchedulePlaceSelectionResponse {
        places = places.stream()
                .map(List::copyOf)
                .toList();
    }
}
