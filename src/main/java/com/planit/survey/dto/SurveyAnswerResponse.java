package com.planit.survey.dto;

public record SurveyAnswerResponse(
        String questionId,
        int score
) {
}
