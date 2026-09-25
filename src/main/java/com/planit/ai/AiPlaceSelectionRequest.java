package com.planit.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record AiPlaceSelectionRequest(
        String region,
        @JsonProperty("start_date")
        LocalDate startDate,
        @JsonProperty("end_date")
        LocalDate endDate,
        List<MemberSurvey> members
) {

    public record MemberSurvey(
            User user,
            @JsonProperty("survey_result")
            List<Integer> surveyResult,
            @JsonProperty("deal_breakers")
            List<String> dealBreakers
    ) {
    }

    public record User(
            @JsonProperty("user_id")
            String userId
    ) {
    }
}
