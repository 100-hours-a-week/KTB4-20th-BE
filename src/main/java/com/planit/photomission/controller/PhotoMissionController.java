package com.planit.photomission.controller;

import com.planit.global.response.ApiResponse;
import com.planit.photomission.ai.PhotoMissionVerifyResponse;
import com.planit.photomission.dto.PhotoMissionAlbumResponse;
import com.planit.photomission.dto.PhotoMissionDetailResponse;
import com.planit.photomission.dto.PhotoMissionListResponse;
import com.planit.photomission.service.PhotoMissionQueryService;
import com.planit.photomission.service.PhotoMissionSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api")
@RequiredArgsConstructor
public class PhotoMissionController {

    private final PhotoMissionSubmissionService submissionService;
    private final PhotoMissionQueryService queryService;

    @PostMapping(path = "/photomissions/{missionId}/photos", consumes = "multipart/form-data")
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

    @GetMapping("/trips/{tripId}/photomissions")
    public ApiResponse<PhotoMissionListResponse> getPhotoMissions(
            Authentication authentication,
            @PathVariable Long tripId,
            @RequestParam(required = false) Integer day
    ) {
        return ApiResponse.success(
                "PHOTO_MISSION_LIST_FOUND",
                "포토미션 목록을 조회했습니다.",
                queryService.getMissionList(
                        authentication.getName(),
                        tripId,
                        day
                )
        );
    }

    @GetMapping("/photomissions/{missionId}")
    public ApiResponse<PhotoMissionDetailResponse> getPhotoMissionDetail(
            Authentication authentication,
            @PathVariable Long missionId
    ) {
        return ApiResponse.success(
                "PHOTO_MISSION_DETAIL_FOUND",
                "포토미션 상세를 조회했습니다.",
                queryService.getMissionDetail(
                        authentication.getName(),
                        missionId
                )
        );
    }

    @GetMapping("/trips/{tripId}/photomissions/album")
    public ApiResponse<PhotoMissionAlbumResponse> getPhotoMissionAlbum(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        return ApiResponse.success(
                "PHOTO_MISSION_ALBUM_FOUND",
                "포토미션 앨범을 조회했습니다.",
                queryService.getAlbum(authentication.getName(), tripId)
        );
    }
}
