package com.planit.schedule.controller;

import com.planit.ai.AiPlaceSelectionResponse;
import com.planit.global.error.GlobalExceptionHandler;
import com.planit.schedule.dto.SchedulePlaceSelectionResponse;
import com.planit.schedule.service.ScheduleGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScheduleGenerationControllerTest {

    private static final String USER_PUBLIC_ID =
            "01991f6e-7300-7b21-a3cc-1436db3df95e";

    private ScheduleGenerationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ScheduleGenerationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ScheduleGenerationController(service)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void generatesSchedulePlaceCandidates() throws Exception {
        when(service.generate(USER_PUBLIC_ID, 100L))
                .thenReturn(new SchedulePlaceSelectionResponse(
                        "100",
                        List.of(new AiPlaceSelectionResponse.Place(
                                "google-place-1",
                                new AiPlaceSelectionResponse.DisplayName(
                                        "경복궁",
                                        "ko"
                                ),
                                new AiPlaceSelectionResponse.Location(
                                        37.5796,
                                        126.9770
                                ),
                                List.of("historical_landmark", "museum"),
                                4.6,
                                4820,
                                null,
                                List.of("user_id_1", "user_id_3"),
                                List.of("HISTORY_CULTURE")
                        ))
                ));

        mockMvc.perform(post("/api/trips/100/schedule-generation")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("SCHEDULE_PLACE_SELECTION_COMPLETED"))
                .andExpect(jsonPath("$.data.tripId").value("100"))
                .andExpect(jsonPath("$.data.places[0].id")
                        .value("google-place-1"))
                .andExpect(jsonPath("$.data.places[0].selected_for[0]")
                        .value("user_id_1"))
                .andExpect(jsonPath(
                        "$.data.places[0].matched_preferences[0]"
                ).value("HISTORY_CULTURE"));

        verify(service).generate(USER_PUBLIC_ID, 100L);
    }
}
