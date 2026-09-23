package com.planit.survey.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record SurveySaveRequest(
        @NotEmpty List<@Valid SurveyAnswerRequest> answers,
        @NotNull List<
                @Pattern(regexp = "^[1-9][0-9]*$") String
        > excludedCategoryIds
) {
}
