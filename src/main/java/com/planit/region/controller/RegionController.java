package com.planit.region.controller;

import com.planit.global.response.ApiResponse;
import com.planit.region.dto.RegionListResponse;
import com.planit.region.service.RegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/regions")
@RequiredArgsConstructor
public class RegionController {

    private final RegionService regionService;

    @GetMapping
    public ApiResponse<RegionListResponse> getRegions() {
        return ApiResponse.success(
                "REGIONS_RETRIEVED",
                "지역 목록을 조회했습니다.",
                regionService.getRegions()
        );
    }
}
