package com.planit.trip.service;

import com.planit.domain.User;
import com.planit.trip.dto.TripCreateRequest;
import com.planit.trip.dto.TripCreateResponse;
import com.planit.trip.dto.TripDetailResponse;
import com.planit.trip.dto.TripJoinRequest;
import com.planit.trip.dto.TripJoinResponse;
import com.planit.trip.dto.TripInvitationPreviewResponse;
import com.planit.trip.dto.TripLeaveResponse;
import com.planit.trip.dto.TripListResponse;

import java.time.LocalDateTime;

public interface TripService {

    TripCreateResponse createTrip(
            String userPublicId,
            TripCreateRequest request
    );

    TripJoinResponse joinTrip(
            String userPublicId,
            TripJoinRequest request
    );

    TripInvitationPreviewResponse getInvitationPreview(
            String userPublicId,
            String invitationToken
    );

    TripListResponse getTrips(
            String userPublicId,
            String cursor,
            int size
    );

    TripDetailResponse getTripDetail(
            String userPublicId,
            Long tripId
    );

    TripLeaveResponse leaveTrip(
            String userPublicId,
            Long tripId
    );

    TripCreateResponse getInvitation(
            String userPublicId,
            Long tripId
    );

    /** 회원 탈퇴 시, 이 사용자가 활성 멤버로 남아있는 모든 여행방에서 나가게 한다. */
    void leaveAllTripsForWithdrawal(
            User user,
            LocalDateTime leftAt
    );
}
