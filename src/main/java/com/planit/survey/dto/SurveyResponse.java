package com.planit.survey.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SurveyResponse(
        String tripId,
        String status,
        OffsetDateTime submittedAt,
        List<SurveyAnswerResponse> answers
) {

    public SurveyResponse {
        answers = List.copyOf(answers);
    }
}
