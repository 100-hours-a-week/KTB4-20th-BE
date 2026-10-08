package com.planit.schedule.controller;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.global.error.GlobalExceptionHandler;
import com.planit.schedule.dto.ScheduleDetailResponse;
import com.planit.schedule.dto.ScheduleStopDeleteResponse;
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
}
