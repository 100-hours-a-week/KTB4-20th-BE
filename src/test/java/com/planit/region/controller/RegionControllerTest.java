package com.planit.region.controller;

import com.planit.region.dto.RegionListResponse;
import com.planit.region.service.RegionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegionControllerTest {

    @Test
    void getsRegions() throws Exception {
        RegionService service = mock(RegionService.class);
        when(service.getRegions()).thenReturn(new RegionListResponse(
                List.of(new RegionListResponse.RegionItem(
                        "1",
                        "REGION-SEOUL",
                        "서울",
                        new BigDecimal("37.566500"),
                        new BigDecimal("126.978000")
                ))
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new RegionController(service)
        ).build();

        mockMvc.perform(get("/api/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REGIONS_RETRIEVED"))
                .andExpect(jsonPath("$.message")
                        .value("지역 목록을 조회했습니다."))
                .andExpect(jsonPath("$.data.regions[0].regionId")
                        .value("1"))
                .andExpect(jsonPath("$.data.regions[0].regionCode")
                        .value("REGION-SEOUL"))
                .andExpect(jsonPath("$.data.regions[0].regionName")
                        .value("서울"))
                .andExpect(jsonPath("$.data.regions[0].latitude")
                        .value(37.566500))
                .andExpect(jsonPath("$.data.regions[0].longitude")
                        .value(126.978000));
    }
}
