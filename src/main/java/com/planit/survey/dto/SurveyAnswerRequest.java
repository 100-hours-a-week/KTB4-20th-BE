package com.planit.survey.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SurveyAnswerRequest(
        @NotBlank
        @Pattern(regexp = "^[1-9][0-9]*$")
        String questionId,

        @Min(1)
        @Max(5)
        int score
) {
}
