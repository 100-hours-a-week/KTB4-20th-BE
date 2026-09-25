package com.planit.survey.service;

import com.planit.domain.PreferenceQuestion;
import com.planit.domain.SurveyExclusionCategory;
import com.planit.repository.PreferenceQuestionRepository;
import com.planit.repository.SurveyExclusionCategoryRepository;
import com.planit.survey.dto.SurveyCatalogResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SurveyCatalogServiceTest {

    @Test
    void returnsQuestionsAndExclusionCategoriesInStoredOrder() {
        PreferenceQuestionRepository questionRepository = mock(
                PreferenceQuestionRepository.class
        );
        SurveyExclusionCategoryRepository exclusionRepository = mock(
                SurveyExclusionCategoryRepository.class
        );
        SurveyCatalogService service = new SurveyCatalogService(
                questionRepository,
                exclusionRepository
        );

        PreferenceQuestion question = mock(PreferenceQuestion.class);
        when(question.getId()).thenReturn(1L);
        when(question.getCode()).thenReturn("HISTORY_CULTURE_MUSEUM");
        when(question.getCategoryCode()).thenReturn("HISTORY_CULTURE");
        when(question.getQuestionText()).thenReturn("문항");
        when(question.getDisplayOrder()).thenReturn((short) 1);
        when(questionRepository.findAllByOrderByDisplayOrderAsc())
                .thenReturn(List.of(question));

        SurveyExclusionCategory exclusion = mock(
                SurveyExclusionCategory.class
        );
        when(exclusion.getId()).thenReturn(1L);
        when(exclusion.getCode()).thenReturn("NOISY_PLACE");
        when(exclusion.getName()).thenReturn("시끄러운_곳");
        when(exclusionRepository.findAllByOrderByIdAsc())
                .thenReturn(List.of(exclusion));

        SurveyCatalogResponse response = service.getSurveyCatalog();

        assertThat(response.questions()).hasSize(1);
        assertThat(response.questions().getFirst().questionId())
                .isEqualTo("1");
        assertThat(response.exclusionCategories()).hasSize(1);
        assertThat(response.exclusionCategories().getFirst().code())
                .isEqualTo("NOISY_PLACE");
    }
}
