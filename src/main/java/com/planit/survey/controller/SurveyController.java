package com.planit.survey.controller;

import com.planit.global.response.ApiResponse;
import com.planit.survey.dto.SurveyResponse;
import com.planit.survey.dto.SurveySaveRequest;
import com.planit.survey.service.SurveyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips/{tripId}/survey")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;

    @GetMapping
    public ApiResponse<SurveyResponse> getMySurvey(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        SurveyResponse response = surveyService.getMySurvey(
                authentication.getName(),
                tripId
        );

        return ApiResponse.success(
                "SURVEY_RETRIEVED",
                "설문을 조회했습니다.",
                response
        );
    }

    @PutMapping
    public ApiResponse<SurveyResponse> saveMySurvey(
            Authentication authentication,
            @PathVariable Long tripId,
            @Valid @RequestBody SurveySaveRequest request
    ) {
        SurveyResponse response = surveyService.saveMySurvey(
                authentication.getName(),
                tripId,
                request
        );

        return ApiResponse.success(
                "SURVEY_SAVED",
                "설문을 저장했습니다.",
                response
        );
    }
}
