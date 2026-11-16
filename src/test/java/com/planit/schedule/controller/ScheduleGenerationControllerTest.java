package com.planit.schedule.controller;

import com.planit.ai.AiPlaceSelectionResponse;
import com.planit.global.error.GlobalExceptionHandler;
import com.planit.schedule.domain.ScheduleGenerationReason;
import com.planit.schedule.dto.SchedulePlaceSelectionResponse;
import com.planit.schedule.service.ScheduleGenerationIntakeService;
import com.planit.schedule.service.ScheduleGenerationService;
import com.planit.mq.intake.AiJobIntakeGuard;
import com.planit.mq.job.AiJobType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScheduleGenerationControllerTest {

    private static final String USER_PUBLIC_ID =
            "01991f6e-7300-7b21-a3cc-1436db3df95e";

    private ScheduleGenerationService service;
    private ScheduleGenerationIntakeService intakeService;
    private AiJobIntakeGuard intakeGuard;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ScheduleGenerationService.class);
        intakeService = mock(ScheduleGenerationIntakeService.class);
        intakeGuard = mock(AiJobIntakeGuard.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ScheduleGenerationController(service, intakeService, intakeGuard)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @DisplayName("일정에 사용할 장소 후보를 생성한다")
    @Test
    void generatesSchedulePlaceCandidates() throws Exception {
        when(service.generate(
                USER_PUBLIC_ID,
                100L,
                ScheduleGenerationReason.GENERAL
        ))
                .thenReturn(new SchedulePlaceSelectionResponse(
                        "100",
                        List.of(List.of(new AiPlaceSelectionResponse.Place(
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
                        )))
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
                .andExpect(jsonPath("$.data.places[0][0].id")
                        .value("google-place-1"))
                .andExpect(jsonPath("$.data.places[0][0].selected_for[0]")
                        .value("user_id_1"))
                .andExpect(jsonPath(
                        "$.data.places[0][0].matched_preferences[0]"
                ).value("HISTORY_CULTURE"));

        verify(service).generate(
                USER_PUBLIC_ID,
                100L,
                ScheduleGenerationReason.GENERAL
        );
    }

    @DisplayName("요청 본문의 사유로 날씨 재생성을 요청한다")
    @Test
    void generatesWithWeatherReason() throws Exception {
        when(service.generate(
                USER_PUBLIC_ID,
                100L,
                ScheduleGenerationReason.WEATHER
        )).thenReturn(new SchedulePlaceSelectionResponse("100", List.of()));

        mockMvc.perform(post("/api/trips/100/schedule-generation")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"WEATHER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tripId").value("100"));

        verify(service).generate(
                USER_PUBLIC_ID,
                100L,
                ScheduleGenerationReason.WEATHER
        );
    }

    @DisplayName("전환 플래그가 켜져 있으면 Idempotency-Key와 함께 접수하고 202로 작업 ID를 문자열로 돌려준다")
    @Test
    void acceptsAsyncWhenFlagOn() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        when(intakeGuard.isAsync(AiJobType.SCHEDULE_GENERATE)).thenReturn(true);
        when(intakeService.accept(USER_PUBLIC_ID, 100L, ScheduleGenerationReason.WEATHER, idempotencyKey))
                .thenReturn(42L);

        mockMvc.perform(post("/api/trips/100/schedule-generation")
                        .principal(new TestingAuthenticationToken(USER_PUBLIC_ID, null))
                        .header("Idempotency-Key", idempotencyKey.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"WEATHER\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("AI_JOB_ACCEPTED"))
                .andExpect(jsonPath("$.data.jobType").value("SCHEDULE_GENERATE"))
                .andExpect(jsonPath("$.data.jobId").value("42"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));

        verifyNoInteractions(service);
    }

    @DisplayName("Idempotency-Key 없이 비동기로 요청하면 서버가 키를 만들도록 넘긴다")
    @Test
    void acceptsAsyncWithoutIdempotencyKey() throws Exception {
        when(intakeGuard.isAsync(AiJobType.SCHEDULE_GENERATE)).thenReturn(true);
        when(intakeService.accept(USER_PUBLIC_ID, 100L, ScheduleGenerationReason.GENERAL, null)).thenReturn(42L);

        mockMvc.perform(post("/api/trips/100/schedule-generation")
                        .principal(new TestingAuthenticationToken(USER_PUBLIC_ID, null)))
                .andExpect(status().isAccepted());

        verify(intakeService).accept(USER_PUBLIC_ID, 100L, ScheduleGenerationReason.GENERAL, null);
    }

    @DisplayName("비동기 요청의 Idempotency-Key가 UUID가 아니면 접수하지 않고 400으로 거부한다")
    @Test
    void rejectsInvalidIdempotencyKey() throws Exception {
        when(intakeGuard.isAsync(AiJobType.SCHEDULE_GENERATE)).thenReturn(true);

        mockMvc.perform(post("/api/trips/100/schedule-generation")
                        .principal(new TestingAuthenticationToken(USER_PUBLIC_ID, null))
                        .header("Idempotency-Key", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(intakeService, service);
    }

    @DisplayName("전환 플래그가 꺼져 있으면 Idempotency-Key가 있어도 기존 동기 경로로 처리한다")
    @Test
    void generatesSyncWhenFlagOff() throws Exception {
        when(service.generate(USER_PUBLIC_ID, 100L, ScheduleGenerationReason.GENERAL))
                .thenReturn(new SchedulePlaceSelectionResponse("100", List.of()));

        mockMvc.perform(post("/api/trips/100/schedule-generation")
                        .principal(new TestingAuthenticationToken(USER_PUBLIC_ID, null))
                        .header("Idempotency-Key", "not-a-uuid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SCHEDULE_PLACE_SELECTION_COMPLETED"));

        verifyNoInteractions(intakeService);
    }
}
