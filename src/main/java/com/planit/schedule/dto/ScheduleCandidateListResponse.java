package com.planit.schedule.dto;

import java.util.List;

public record ScheduleCandidateListResponse(
        String tripId,
        List<Candidate> candidates
) {
    public ScheduleCandidateListResponse {
        candidates = List.copyOf(candidates);
    }

    public record Candidate(
            String scheduleId,
            String strategy,
            String summary,
            Integer preferenceCount,
            Integer discoveryCount,
            int totalDistanceMeters
    ) {
    }
}
