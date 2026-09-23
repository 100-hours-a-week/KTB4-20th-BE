package com.planit.survey.controller;

import com.planit.global.error.GlobalExceptionHandler;
import com.planit.survey.dto.SurveyAnswerResponse;
import com.planit.survey.dto.SurveyResponse;
import com.planit.survey.service.SurveyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SurveyControllerTest {

    private static final String USER_PUBLIC_ID =
            "01991f6e-7300-7b21-a3cc-1436db3df95e";

    private SurveyService surveyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        surveyService = mock(SurveyService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SurveyController(surveyService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getsMySurvey() throws Exception {
        when(surveyService.getMySurvey(USER_PUBLIC_ID, 1001L))
                .thenReturn(new SurveyResponse(
                        "1001",
                        "DRAFT",
                        null,
                        List.of(new SurveyAnswerResponse("10", 3)),
                        List.of()
                ));

        mockMvc.perform(get("/api/trips/1001/survey")
                        .principal(authentication()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SURVEY_RETRIEVED"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.answers[0].questionId").value("10"))
                .andExpect(jsonPath("$.data.answers[0].score").value(3));

        verify(surveyService).getMySurvey(USER_PUBLIC_ID, 1001L);
    }

    @Test
    void savesMySurvey() throws Exception {
        OffsetDateTime submittedAt = OffsetDateTime.parse(
                "2026-09-23T12:00:00.123456+09:00"
        );
        when(surveyService.saveMySurvey(
                org.mockito.ArgumentMatchers.eq(USER_PUBLIC_ID),
                org.mockito.ArgumentMatchers.eq(1001L),
                any()
        )).thenReturn(new SurveyResponse(
                "1001",
                "SUBMITTED",
                submittedAt,
                List.of(new SurveyAnswerResponse("10", 4)),
                List.of("1")
        ));

        mockMvc.perform(put("/api/trips/1001/survey")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": "10", "score": 4}
                                  ],
                                  "excludedCategoryIds": ["1"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SURVEY_SAVED"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.submittedAt")
                        .value("2026-09-23T12:00:00.123456+09:00"))
                .andExpect(jsonPath("$.data.answers[0].score").value(4))
                .andExpect(jsonPath("$.data.excludedCategoryIds[0]")
                        .value("1"));
    }

    @Test
    void rejectsScoreOutsideFivePointScale() throws Exception {
        mockMvc.perform(put("/api/trips/1001/survey")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": "10", "score": 6}
                                  ],
                                  "excludedCategoryIds": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void requiresExcludedCategoryIds() throws Exception {
        mockMvc.perform(put("/api/trips/1001/survey")
                        .principal(authentication())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "answers": [
                                    {"questionId": "10", "score": 3}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private TestingAuthenticationToken authentication() {
        return new TestingAuthenticationToken(USER_PUBLIC_ID, null);
    }
}
