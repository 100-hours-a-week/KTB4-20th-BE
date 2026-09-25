package com.planit.trip.controller;

import com.planit.global.error.GlobalExceptionHandler;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.trip.dto.TripInvitationPreviewResponse;
import com.planit.trip.service.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.UUID;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripInvitationControllerTest {

    private static final String USER_PUBLIC_ID =
            "01991f6e-7300-7b21-a3cc-1436db3df95e";
    private static final String INVITATION_TOKEN = "a".repeat(43);

    private TripService tripService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tripService = mock(TripService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new TripInvitationController(tripService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void retrievesInvitationPreview() throws Exception {
        when(tripService.getInvitationPreview(
                USER_PUBLIC_ID,
                INVITATION_TOKEN
        )).thenReturn(new TripInvitationPreviewResponse(
                new TripInvitationPreviewResponse.Trip(
                        "1001",
                        "부산 맛집 여행",
                        new TripInvitationPreviewResponse.Region(
                                "123",
                                "부산광역시",
                                "해운대구"
                        ),
                        LocalDate.of(2026, 9, 12),
                        LocalDate.of(2026, 9, 14),
                        3,
                        4
                ),
                new TripInvitationPreviewResponse.Inviter(
                        UUID.fromString(USER_PUBLIC_ID),
                        "플랜잇방장",
                        "https://example.com/default-profile.png"
                ),
                List.of(new TripInvitationPreviewResponse.Member(
                        "플랜잇방장",
                        "https://example.com/default-profile.png"
                )),
                true,
                null
        ));

        mockMvc.perform(get(
                                "/api/invitations/{invitationToken}",
                                INVITATION_TOKEN
                        )
                        .principal(new TestingAuthenticationToken(
                                USER_PUBLIC_ID,
                                null
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("INVITATION_RETRIEVED"))
                .andExpect(jsonPath("$.message")
                        .value("초대 정보를 조회했습니다."))
                .andExpect(jsonPath("$.data.trip.tripId")
                        .value("1001"))
                .andExpect(jsonPath("$.data.trip.region.regionId")
                        .value("123"))
                .andExpect(jsonPath("$.data.inviter.userName")
                        .value("플랜잇방장"))
                .andExpect(jsonPath("$.data.members[0].userName")
                        .value("플랜잇방장"))
                .andExpect(jsonPath("$.data.alreadyJoined")
                        .value(true));

        verify(tripService).getInvitationPreview(
                USER_PUBLIC_ID,
                INVITATION_TOKEN
        );
    }

    @Test
    void returnsAuthenticationRequiredAfterValidInvitationCheck()
            throws Exception {
        when(tripService.getInvitationPreview(null, INVITATION_TOKEN))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED
                ));

        mockMvc.perform(get(
                        "/api/invitations/{invitationToken}",
                        INVITATION_TOKEN
                ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTHENTICATION_REQUIRED"));

        verify(tripService).getInvitationPreview(null, INVITATION_TOKEN);
    }
}
