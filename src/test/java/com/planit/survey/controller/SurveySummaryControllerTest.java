package com.planit.survey.controller;

import com.planit.global.error.GlobalExceptionHandler;
import com.planit.survey.dto.SurveySummaryResponse;
import com.planit.survey.service.SurveyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SurveySummaryControllerTest {

    private static final String USER_PUBLIC_ID =
            "01991f6e-7300-7b21-a3cc-1436db3df95e";

    private SurveyService surveyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        surveyService = mock(SurveyService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SurveySummaryController(surveyService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getsSurveySummary() throws Exception {
        UUID publicId = UUID.fromString(USER_PUBLIC_ID);
        when(surveyService.getSurveySummary(USER_PUBLIC_ID, 1001L))
                .thenReturn(new SurveySummaryResponse(
                        "1001",
                        OffsetDateTime.parse("2026-09-25T23:59:59+09:00"),
                        1,
                        1,
                        100,
                        true,
                        true,
                        false,
                        List.of(new SurveySummaryResponse.MemberSubmission(
                                publicId,
                                true
                        )),
                        List.of(new SurveySummaryResponse.CategoryAverage(
                                "FOOD",
                                4.0,
                                75
                        )),
                        List.of(new SurveySummaryResponse.ExcludedCategory(
                                "SEAFOOD",
                                "해산물"
                        ))
                ));

        mockMvc.perform(get("/api/trips/1001/survey-summary")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("SURVEY_SUMMARY_RETRIEVED"))
                .andExpect(jsonPath("$.message")
                        .value("설문 현황과 그룹 취향을 조회했습니다."))
                .andExpect(jsonPath("$.data.submittedCount").value(1))
                .andExpect(jsonPath(
                        "$.data.memberSubmissions[0].submitted"
                ).value(true))
                .andExpect(jsonPath(
                        "$.data.categoryAverages[0].preferencePercent"
                ).value(75))
                .andExpect(jsonPath("$.data.excludedCategories[0].code")
                        .value("SEAFOOD"));

        verify(surveyService).getSurveySummary(USER_PUBLIC_ID, 1001L);
    }
}
