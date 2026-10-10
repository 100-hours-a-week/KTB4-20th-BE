package com.planit.schedule.controller;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.global.error.GlobalExceptionHandler;
import com.planit.schedule.dto.ScheduleDetailResponse;
import com.planit.schedule.dto.ScheduleStopDeleteResponse;
import com.planit.schedule.dto.ScheduleStopAddRequest;
import com.planit.schedule.dto.ScheduleStopAddResponse;
import com.planit.schedule.service.ScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScheduleControllerTest {

    private static final String USER_PUBLIC_ID =
            "01991f6e-7300-7b21-a3cc-1436db3df95e";

    private ScheduleService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ScheduleService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ScheduleController(service)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @DisplayName("확정 일정의 장소를 삭제한다")
    @Test
    void deletesScheduleStop() throws Exception {
        ScheduleDetailResponse.Day day = new ScheduleDetailResponse.Day(
                "20",
                1,
                LocalDate.of(2026, 10, 20),
                0,
                List.of(),
                List.of()
        );
        when(service.deleteStop(USER_PUBLIC_ID, 1L, 101L))
                .thenReturn(new ScheduleStopDeleteResponse(
                        "10",
                        "101",
                        day
                ));

        mockMvc.perform(delete("/api/trips/1/schedule/stops/101")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SCHEDULE_STOP_DELETED"))
                .andExpect(jsonPath("$.data.scheduleId").value("10"))
                .andExpect(jsonPath("$.data.deletedStopId").value("101"));

        verify(service).deleteStop(USER_PUBLIC_ID, 1L, 101L);
    }

    @DisplayName("장소가 세 개뿐이면 삭제 요청을 거부한다")
    @Test
    void rejectsDeletionBelowMinimum() throws Exception {
        when(service.deleteStop(USER_PUBLIC_ID, 1L, 101L))
                .thenThrow(new BusinessException(
                        ErrorCode.SCHEDULE_MINIMUM_STOPS_REQUIRED
                ));

        mockMvc.perform(delete("/api/trips/1/schedule/stops/101")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("SCHEDULE_MINIMUM_STOPS_REQUIRED"));
    }

    @DisplayName("Google 장소 정보를 받아 확정 일정에 장소를 추가한다")
    @Test
    void addsScheduleStop() throws Exception {
        ScheduleDetailResponse.Day day = new ScheduleDetailResponse.Day(
                "20",
                1,
                LocalDate.of(2026, 10, 20),
                0,
                List.of(),
                List.of()
        );
        when(service.addStop(
                org.mockito.ArgumentMatchers.eq(USER_PUBLIC_ID),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(ScheduleStopAddRequest.class)
        )).thenReturn(new ScheduleStopAddResponse("10", day));

        mockMvc.perform(post("/api/trips/1/schedule/stops")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        ))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "scheduleDayId": 20,
                                  "position": 3,
                                  "place": {
                                    "googlePlaceId": "ChIJ-example",
                                    "name": "경주 카페",
                                    "categoryName": "카페",
                                    "address": "경주시 주소",
                                    "roadAddress": null,
                                    "longitude": 129.2107,
                                    "latitude": 35.8381,
                                    "phone": "054-000-0000",
                                    "placeUrl": "https://maps.example/place"
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SCHEDULE_STOP_ADDED"))
                .andExpect(jsonPath("$.data.scheduleId").value("10"));
    }

    @DisplayName("장소 이름이 비어 있으면 추가 요청을 거부한다")
    @Test
    void rejectsInvalidAddRequest() throws Exception {
        mockMvc.perform(post("/api/trips/1/schedule/stops")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        ))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "scheduleDayId": 20,
                                  "position": 1,
                                  "place": {
                                    "googlePlaceId": "ChIJ-example",
                                    "name": " ",
                                    "longitude": 129.2107,
                                    "latitude": 35.8381
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
