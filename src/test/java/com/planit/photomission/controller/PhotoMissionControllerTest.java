package com.planit.photomission.controller;

import com.planit.photomission.ai.PhotoMissionVerifyResponse;
import com.planit.photomission.dto.PhotoMissionAlbumResponse;
import com.planit.photomission.dto.PhotoMissionDetailResponse;
import com.planit.photomission.dto.PhotoMissionListResponse;
import com.planit.photomission.service.PhotoMissionQueryService;
import com.planit.photomission.service.PhotoMissionSubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PhotoMissionControllerTest {

    private PhotoMissionSubmissionService submissionService;
    private PhotoMissionQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        submissionService = mock(PhotoMissionSubmissionService.class);
        queryService = mock(PhotoMissionQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PhotoMissionController(submissionService, queryService)
        ).build();
    }

    @DisplayName("사진을 제출하고 동기 AI 판정 결과를 반환한다")
    @Test
    void submitsAndVerifiesPhoto() throws Exception {
        when(submissionService.submitAndVerify(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(new PhotoMissionVerifyResponse(
                "retry",
                null,
                BigDecimal.valueOf(75),
                List.of(),
                BigDecimal.valueOf(92),
                "한 발 옆으로 옮겨서 다시 찍어 보세요."
        ));
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "photo.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
        );

        mockMvc.perform(multipart("/api/photomissions/31/photos")
                        .file(photo)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .param("latitude", "35.8350")
                        .param("longitude", "129.2188")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "550e8400-e29b-41d4-a716-446655440000",
                                ""
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("PHOTO_MISSION_VERIFIED"))
                .andExpect(jsonPath("$.data.result").value("retry"))
                .andExpect(jsonPath("$.data.retry_hint")
                        .value("한 발 옆으로 옮겨서 다시 찍어 보세요."));
    }

    @DisplayName("선택한 Day의 포토미션 목록을 조회한다")
    @Test
    void getsPhotoMissionsByDay() throws Exception {
        when(queryService.getMissionList(any(), any(), any()))
                .thenReturn(new PhotoMissionListResponse(
                        new PhotoMissionListResponse.TripSummary(
                                10L,
                                "경주 여행",
                                LocalDate.of(2026, 8, 31),
                                LocalDate.of(2026, 9, 2),
                                4
                        ),
                        List.of(
                                new PhotoMissionListResponse.DayTab(
                                        1,
                                        LocalDate.of(2026, 8, 31),
                                        true,
                                        false
                                ),
                                new PhotoMissionListResponse.DayTab(
                                        2,
                                        LocalDate.of(2026, 9, 1),
                                        true,
                                        true
                                ),
                                new PhotoMissionListResponse.DayTab(
                                        3,
                                        LocalDate.of(2026, 9, 2),
                                        false,
                                        false
                                )
                        ),
                        new PhotoMissionListResponse.SelectedDay(
                                2,
                                LocalDate.of(2026, 9, 1),
                                true,
                                2,
                                5,
                                false,
                                true,
                                List.of()
                        )
                ));

        mockMvc.perform(get("/api/trips/10/photomissions")
                        .param("day", "2")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "550e8400-e29b-41d4-a716-446655440000",
                                ""
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("PHOTO_MISSION_LIST_FOUND"))
                .andExpect(jsonPath("$.data.trip.name")
                        .value("경주 여행"))
                .andExpect(jsonPath("$.data.days[1].selected")
                        .value(true))
                .andExpect(jsonPath("$.data.selectedDay.completedMissionCount")
                        .value(2));
    }

    @DisplayName("포토미션 상세를 조회한다")
    @Test
    void getsPhotoMissionDetail() throws Exception {
        when(queryService.getMissionDetail(any(), any()))
                .thenReturn(new PhotoMissionDetailResponse(
                        31L,
                        2,
                        LocalDate.of(2026, 9, 1),
                        "PERSONAL",
                        "유적지 전경 담기",
                        "역사·유적",
                        false,
                        "PENDING",
                        1,
                        null,
                        null,
                        List.of(),
                        List.of()
                ));

        mockMvc.perform(get("/api/photomissions/31")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "550e8400-e29b-41d4-a716-446655440000",
                                ""
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("PHOTO_MISSION_DETAIL_FOUND"))
                .andExpect(jsonPath("$.data.missionId").value(31))
                .andExpect(jsonPath("$.data.scope").value("PERSONAL"))
                .andExpect(jsonPath("$.data.retryCount").value(1));
    }

    @DisplayName("여행의 개인·단체 포토미션 앨범을 조회한다")
    @Test
    void getsPhotoMissionAlbum() throws Exception {
        when(queryService.getAlbum(any(), any()))
                .thenReturn(new PhotoMissionAlbumResponse(
                        10L,
                        "경주 여행",
                        2,
                        3,
                        List.of()
                ));

        mockMvc.perform(get("/api/trips/10/photomissions/album")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "550e8400-e29b-41d4-a716-446655440000",
                                ""
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("PHOTO_MISSION_ALBUM_FOUND"))
                .andExpect(jsonPath("$.data.completedMissionCount")
                        .value(2))
                .andExpect(jsonPath("$.data.totalPhotoCount").value(3));
    }
}
