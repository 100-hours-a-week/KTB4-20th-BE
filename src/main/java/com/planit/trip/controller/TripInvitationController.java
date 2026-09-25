package com.planit.trip.controller;

import com.planit.global.response.ApiResponse;
import com.planit.trip.dto.TripInvitationPreviewResponse;
import com.planit.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitations")
@RequiredArgsConstructor
public class TripInvitationController {

    private static final String SUCCESS_CODE = "INVITATION_RETRIEVED";
    private static final String SUCCESS_MESSAGE = "초대 정보를 조회했습니다.";

    private final TripService tripService;

    @GetMapping("/{invitationToken}")
    public ApiResponse<TripInvitationPreviewResponse> getInvitationPreview(
            Authentication authentication,
            @PathVariable String invitationToken
    ) {
        TripInvitationPreviewResponse response =
                tripService.getInvitationPreview(
                        authentication == null
                                ? null
                                : authentication.getName(),
                        invitationToken
                );

        return ApiResponse.success(
                SUCCESS_CODE,
                SUCCESS_MESSAGE,
                response
        );
    }
}
