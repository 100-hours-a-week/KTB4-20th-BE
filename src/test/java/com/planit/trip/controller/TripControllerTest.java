package com.planit.trip.controller;

import com.planit.domain.TripMemberRole;
import com.planit.domain.TripProgressStatus;
import com.planit.global.error.GlobalExceptionHandler;
import com.planit.trip.dto.TripCreateRequest;
import com.planit.trip.dto.TripCreateResponse;
import com.planit.trip.dto.TripDetailResponse;
import com.planit.trip.dto.TripJoinRequest;
import com.planit.trip.dto.TripJoinResponse;
import com.planit.trip.dto.TripListResponse;
import com.planit.trip.service.TripService;
import java.util.stream.Stream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void getsParticipatingTrips() throws Exception {
        String cursor = "next-cursor";
        when(tripService.getTrips(USER_PUBLIC_ID, cursor, 5))
                .thenReturn(new TripListResponse(
                        List.of(new TripListResponse.TripSummary(
                                "100",
                                "경주 여행",
                                LocalDate.of(2026, 9, 26),
                                TripProgressStatus.SURVEY_IN_PROGRESS,
                                1,
                                List.of(new TripListResponse.MemberSummary(
                                        "채령",
                                        "https://example.com/profile.png"
                                ))
                        )),
                        "following-cursor",
                        true
                ));

        mockMvc.perform(get("/api/trips")
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        ))
                        .queryParam("cursor", cursor)
                        .queryParam("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("TRIP_LIST_RETRIEVED"))
                .andExpect(jsonPath("$.message")
                        .value("참여 중인 여행방 목록을 조회했습니다."))
                .andExpect(jsonPath("$.data.trips[0].tripId")
                        .value("100"))
                .andExpect(jsonPath("$.data.trips[0].name")
                        .value("경주 여행"))
                .andExpect(jsonPath("$.data.trips[0].status")
                        .value("SURVEY_IN_PROGRESS"))
                .andExpect(jsonPath("$.data.trips[0].memberCount")
                        .value(1))
                .andExpect(jsonPath("$.data.nextCursor")
                        .value("following-cursor"))
                .andExpect(jsonPath("$.data.hasNext").value(true));

        verify(tripService).getTrips(USER_PUBLIC_ID, cursor, 5);
    }

    @Test
    void getsTripDetail() throws Exception {
        when(tripService.getTripDetail(USER_PUBLIC_ID, 1001L))
                .thenReturn(new TripDetailResponse(
                        "1001",
                        "부산 맛집 여행",
                        new TripDetailResponse.Region(
                                "123",
                                "26",
                                "부산광역시",
                                "26350",
                                "해운대구"
                        ),
                        LocalDate.of(2026, 9, 12),
                        LocalDate.of(2026, 9, 14),
                        4,
                        2,
                        TripMemberRole.HOST,
                        OffsetDateTime.parse(
                                "2026-09-11T23:59:59.999999+09:00"
                        ),
                        OffsetDateTime.parse(
                                "2026-09-07T14:30:00.123456+09:00"
                        ),
                        List.of(
                                new TripDetailResponse.Member(
                                        java.util.UUID.fromString(
                                                USER_PUBLIC_ID
                                        ),
                                        "채령",
                                        "https://example.com/default-profile.png",
                                        TripMemberRole.HOST
                                ),
                                new TripDetailResponse.Member(
                                        java.util.UUID.fromString(
                                                "01991f6e-7300-7b21-a3cc-1436db3df95f"
                                        ),
                                        "민수",
                                        "https://example.com/default-profile.png",
                                        TripMemberRole.MEMBER
                                )
                        )
                ));

        mockMvc.perform(get("/api/trips/{tripId}", 1001L)
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TRIP_RETRIEVED"))
                .andExpect(jsonPath("$.message")
                        .value("여행방을 조회했습니다."))
                .andExpect(jsonPath("$.data.tripId").value("1001"))
                .andExpect(jsonPath("$.data.name")
                        .value("부산 맛집 여행"))
                .andExpect(jsonPath("$.data.region.regionId")
                        .value("123"))
                .andExpect(jsonPath("$.data.region.broadRegionCode")
                        .value("26"))
                .andExpect(jsonPath("$.data.region.broadRegionName")
                        .value("부산광역시"))
                .andExpect(jsonPath("$.data.region.subRegionCode")
                        .value("26350"))
                .andExpect(jsonPath("$.data.region.subRegionName")
                        .value("해운대구"))
                .andExpect(jsonPath("$.data.startDate")
                        .value("2026-09-12"))
                .andExpect(jsonPath("$.data.endDate")
                        .value("2026-09-14"))
                .andExpect(jsonPath("$.data.capacity").value(4))
                .andExpect(jsonPath("$.data.memberCount").value(2))
                .andExpect(jsonPath("$.data.myRole").value("HOST"))
                .andExpect(jsonPath("$.data.surveyDeadlineAt")
                        .value("2026-09-11T23:59:59.999999+09:00"))
                .andExpect(jsonPath("$.data.createdAt")
                        .value("2026-09-07T14:30:00.123456+09:00"))
                .andExpect(jsonPath("$.data.members[0].userPublicId")
                        .value(USER_PUBLIC_ID))
                .andExpect(jsonPath("$.data.members[0].userName")
                        .value("채령"))
                .andExpect(jsonPath("$.data.members[0].profileImageUrl")
                        .value("https://example.com/default-profile.png"))
                .andExpect(jsonPath("$.data.members[0].role")
                        .value("HOST"));

        verify(tripService).getTripDetail(USER_PUBLIC_ID, 1001L);
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
