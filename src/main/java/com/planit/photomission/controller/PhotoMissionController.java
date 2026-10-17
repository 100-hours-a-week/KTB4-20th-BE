package com.planit.photomission.controller;

import com.planit.global.response.ApiResponse;
import com.planit.photomission.ai.PhotoMissionVerifyResponse;
import com.planit.photomission.service.PhotoMissionManualCompletionService;
import com.planit.photomission.service.PhotoMissionPhotoManagementService;
import com.planit.photomission.service.PhotoMissionSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/photomissions")
@RequiredArgsConstructor
public class PhotoMissionController {

    private final PhotoMissionSubmissionService submissionService;
    private final PhotoMissionPhotoManagementService photoManagementService;
    private final PhotoMissionManualCompletionService manualCompletionService;

    @PostMapping(path = "/{missionId}/photos", consumes = "multipart/form-data")
    public ApiResponse<PhotoMissionVerifyResponse> submitPhoto(
            Authentication authentication,
            @PathVariable Long missionId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestPart("photo") MultipartFile photo,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude
    ) {
        return ApiResponse.success(
                "PHOTO_MISSION_VERIFIED",
                "사진 판정이 완료되었습니다.",
                submissionService.submitAndVerify(
                        authentication.getName(),
                        missionId,
                        idempotencyKey,
                        photo,
                        latitude,
                        longitude
                )
        );
    }

    @DeleteMapping("/{missionId}/photos/{photoId}")
    public ApiResponse<Void> deletePhoto(
            Authentication authentication,
            @PathVariable Long missionId,
            @PathVariable Long photoId
    ) {
        photoManagementService.delete(
                authentication.getName(),
                missionId,
                photoId
        );

        return ApiResponse.success(
                "PHOTO_MISSION_PHOTO_DELETED",
                "사진을 삭제했습니다.",
                null
        );
    }

    @PostMapping("/{missionId}/manual-completion")
    public ApiResponse<Void> completeManually(
            Authentication authentication,
            @PathVariable Long missionId
    ) {
        manualCompletionService.complete(
                authentication.getName(),
                missionId
        );
        return ApiResponse.success(
                "PHOTO_MISSION_MANUALLY_COMPLETED",
                "미션을 직접 완료했습니다.",
                null
        );
    }

}
