package com.planit.survey.dto;

import java.util.List;

public record SurveyCatalogResponse(
        List<PreferenceQuestionResponse> questions,
        List<SurveyExclusionCategoryResponse> exclusionCategories
) {

    public SurveyCatalogResponse {
        questions = List.copyOf(questions);
        exclusionCategories = List.copyOf(exclusionCategories);
    }
}
