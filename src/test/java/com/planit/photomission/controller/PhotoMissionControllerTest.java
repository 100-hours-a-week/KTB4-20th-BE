package com.planit.photomission.controller;

import com.planit.photomission.ai.PhotoMissionVerifyResponse;
import com.planit.photomission.service.PhotoMissionPhotoManagementService;
import com.planit.photomission.service.PhotoMissionSubmissionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PhotoMissionControllerTest {

    @DisplayName("사진을 제출하고 동기 AI 판정 결과를 반환한다")
    @Test
    void submitsAndVerifiesPhoto() throws Exception {
        PhotoMissionSubmissionService service =
                mock(PhotoMissionSubmissionService.class);
        PhotoMissionPhotoManagementService managementService =
                mock(PhotoMissionPhotoManagementService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new PhotoMissionController(service, managementService)
        ).build();
        when(service.submitAndVerify(
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

    @DisplayName("본인이 제출한 포토미션 사진을 삭제한다")
    @Test
    void deletesSubmittedPhoto() throws Exception {
        PhotoMissionSubmissionService submissionService =
                mock(PhotoMissionSubmissionService.class);
        PhotoMissionPhotoManagementService managementService =
                mock(PhotoMissionPhotoManagementService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new PhotoMissionController(
                        submissionService,
                        managementService
                )
        ).build();

        mockMvc.perform(delete("/api/photomissions/31/photos/41")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "550e8400-e29b-41d4-a716-446655440000",
                                ""
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code")
                        .value("PHOTO_MISSION_PHOTO_DELETED"));

        verify(managementService).delete(
                "550e8400-e29b-41d4-a716-446655440000",
                31L,
                41L
        );
    }

}
