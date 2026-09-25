package com.planit.survey.controller;

import com.planit.global.response.ApiResponse;
import com.planit.survey.dto.SurveySummaryResponse;
import com.planit.survey.service.SurveyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/survey-summary")
@RequiredArgsConstructor
public class SurveySummaryController {

    private final SurveyService surveyService;

    @GetMapping
    public ApiResponse<SurveySummaryResponse> getSurveySummary(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        SurveySummaryResponse response = surveyService.getSurveySummary(
                authentication.getName(),
                tripId
        );

        return ApiResponse.success(
                "SURVEY_SUMMARY_RETRIEVED",
                "설문 현황과 그룹 취향을 조회했습니다.",
                response
        );
    }
}
