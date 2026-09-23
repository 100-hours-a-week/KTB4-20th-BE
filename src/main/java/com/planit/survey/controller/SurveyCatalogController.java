package com.planit.survey.controller;

import com.planit.global.response.ApiResponse;
import com.planit.survey.dto.SurveyCatalogResponse;
import com.planit.survey.service.SurveyCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/preference-questions")
@RequiredArgsConstructor
public class SurveyCatalogController {

    private final SurveyCatalogService surveyCatalogService;

    @GetMapping
    public ApiResponse<SurveyCatalogResponse> getSurveyCatalog() {
        return ApiResponse.success(
                "SURVEY_CATALOG_RETRIEVED",
                "설문 문항과 제외 카테고리를 조회했습니다.",
                surveyCatalogService.getSurveyCatalog()
        );
    }
}
