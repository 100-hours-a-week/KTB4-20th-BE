package com.planit.trip.controller;

import com.planit.global.error.GlobalExceptionHandler;
import com.planit.trip.dto.TripCreateRequest;
import com.planit.trip.dto.TripCreateResponse;
import com.planit.trip.dto.TripJoinRequest;
import com.planit.trip.dto.TripJoinResponse;
import com.planit.trip.service.TripService;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripControllerTest {

    private static final String USER_PUBLIC_ID =
            "01991f6e-7300-7b21-a3cc-1436db3df95e";
    private static final String INVITATION_TOKEN = "a".repeat(43);

    private TripService tripService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tripService = mock(TripService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new TripController(tripService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsTrip() throws Exception {
        when(tripService.createTrip(
                org.mockito.ArgumentMatchers.eq(USER_PUBLIC_ID),
                any(TripCreateRequest.class)
        )).thenReturn(new TripCreateResponse(
                "100",
                INVITATION_TOKEN
        ));

        mockMvc.perform(post("/api/trips")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "제주 여행",
                                  "subRegionId": 1,
                                  "startDate": "2026-10-01",
                                  "capacity": 4,
                                  "surveyDeadlineDate": "2026-09-30"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TRIP_CREATED"))
                .andExpect(jsonPath("$.message")
                        .value("여행방을 생성했습니다."))
                .andExpect(jsonPath("$.data.tripId").value("100"))
                .andExpect(jsonPath("$.data.invitationToken")
                        .value(INVITATION_TOKEN));

        verify(tripService).createTrip(
                org.mockito.ArgumentMatchers.eq(USER_PUBLIC_ID),
                any(TripCreateRequest.class)
        );
    }

    @Test
    void joinsTripUsingInvitationToken() throws Exception {
        when(tripService.joinTrip(
                org.mockito.ArgumentMatchers.eq(USER_PUBLIC_ID),
                any(TripJoinRequest.class)
        )).thenReturn(new TripJoinResponse("100"));

        mockMvc.perform(post("/api/trips/join")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationToken": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TRIP_JOINED"))
                .andExpect(jsonPath("$.message")
                        .value("여행방에 참여했습니다."))
                .andExpect(jsonPath("$.data.tripId").value("100"));

        verify(tripService).joinTrip(
                USER_PUBLIC_ID,
                new TripJoinRequest(INVITATION_TOKEN)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidInvitationTokens")
    void rejectsInvalidInvitationToken(String invitationToken)
            throws Exception {
        mockMvc.perform(post("/api/trips/join")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invitationToken": "%s"
                                }
                                """.formatted(invitationToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field")
                        .value("invitationToken"));

        verify(tripService, never()).joinTrip(any(), any());
    }

    @Test
    void appliesDefaultCapacityAndAcceptsMissingDeadline() throws Exception {
        when(tripService.createTrip(
                org.mockito.ArgumentMatchers.eq(USER_PUBLIC_ID),
                any(TripCreateRequest.class)
        )).thenReturn(new TripCreateResponse(
                "100",
                INVITATION_TOKEN
        ));

        mockMvc.perform(post("/api/trips")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "부산 여행",
                                  "subRegionId": 1,
                                  "startDate": "2026-10-01"
                                }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<TripCreateRequest> captor =
                ArgumentCaptor.forClass(TripCreateRequest.class);
        verify(tripService).createTrip(
                org.mockito.ArgumentMatchers.eq(USER_PUBLIC_ID),
                captor.capture()
        );
        assertThat(captor.getValue().capacity()).isEqualTo(4);
        assertThat(captor.getValue().surveyDeadlineDate()).isNull();
    }

    @Test
    void rejectsInvalidTripName() throws Exception {
        mockMvc.perform(post("/api/trips")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "제주여행1",
                                  "subRegionId": 1,
                                  "startDate": "2026-10-01",
                                  "capacity": 4
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));

        verify(tripService, never()).createTrip(any(), any());
    }

    private static Stream<String> invalidInvitationTokens() {
        return Stream.of(
                "",
                "a".repeat(42),
                "a".repeat(44),
                "a".repeat(42) + "!"
        );
    }
}
