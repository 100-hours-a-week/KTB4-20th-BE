package com.planit.survey.dto;

public record PreferenceQuestionResponse(
        String questionId,
        String code,
        String categoryCode,
        String questionText,
        int displayOrder
) {
}
