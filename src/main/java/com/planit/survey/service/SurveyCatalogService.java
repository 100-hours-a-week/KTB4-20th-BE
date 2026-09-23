package com.planit.survey.service;

import com.planit.repository.PreferenceQuestionRepository;
import com.planit.repository.SurveyExclusionCategoryRepository;
import com.planit.survey.dto.PreferenceQuestionResponse;
import com.planit.survey.dto.SurveyCatalogResponse;
import com.planit.survey.dto.SurveyExclusionCategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyCatalogService {

    private final PreferenceQuestionRepository preferenceQuestionRepository;
    private final SurveyExclusionCategoryRepository
            surveyExclusionCategoryRepository;

    public SurveyCatalogResponse getSurveyCatalog() {
        List<PreferenceQuestionResponse> questions = preferenceQuestionRepository
                .findAllByOrderByDisplayOrderAsc()
                .stream()
                .map(question -> new PreferenceQuestionResponse(
                        question.getId().toString(),
                        question.getCode(),
                        question.getCategoryCode(),
                        question.getQuestionText(),
                        question.getDisplayOrder()
                ))
                .toList();

        List<SurveyExclusionCategoryResponse> exclusionCategories =
                surveyExclusionCategoryRepository
                        .findAllByOrderByIdAsc()
                        .stream()
                        .map(category -> new SurveyExclusionCategoryResponse(
                                category.getId().toString(),
                                category.getCode(),
                                category.getName()
                        ))
                        .toList();

        return new SurveyCatalogResponse(
                questions,
                exclusionCategories
        );
    }
}
