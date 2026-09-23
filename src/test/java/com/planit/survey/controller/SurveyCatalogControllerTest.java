package com.planit.survey.controller;

import com.planit.survey.dto.PreferenceQuestionResponse;
import com.planit.survey.dto.SurveyCatalogResponse;
import com.planit.survey.dto.SurveyExclusionCategoryResponse;
import com.planit.survey.service.SurveyCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SurveyCatalogControllerTest {

    @Test
    void getsSurveyCatalog() throws Exception {
        SurveyCatalogService service = mock(SurveyCatalogService.class);
        when(service.getSurveyCatalog()).thenReturn(new SurveyCatalogResponse(
                List.of(new PreferenceQuestionResponse(
                        "1",
                        "HISTORY_CULTURE_MUSEUM",
                        "HISTORY_CULTURE",
                        "문항",
                        1
                )),
                List.of(new SurveyExclusionCategoryResponse(
                        "1",
                        "NOISY_PLACE",
                        "시끄러운_곳"
                ))
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new SurveyCatalogController(service)
        ).build();

        mockMvc.perform(get("/api/preference-questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("SURVEY_CATALOG_RETRIEVED"))
                .andExpect(jsonPath("$.data.questions[0].questionId")
                        .value("1"))
                .andExpect(jsonPath("$.data.exclusionCategories[0].code")
                        .value("NOISY_PLACE"));
    }
}
