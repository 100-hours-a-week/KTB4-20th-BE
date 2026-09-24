package com.planit.trip.controller;

import com.planit.global.response.ApiResponse;
import com.planit.trip.dto.TripCreateRequest;
import com.planit.trip.dto.TripCreateResponse;
import com.planit.trip.dto.TripDetailResponse;
import com.planit.trip.dto.TripJoinRequest;
import com.planit.trip.dto.TripJoinResponse;
import com.planit.trip.dto.TripListResponse;
import com.planit.trip.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private static final String SUCCESS_CODE = "TRIP_CREATED";
    private static final String SUCCESS_MESSAGE = "여행방을 생성했습니다.";
    private static final String JOIN_SUCCESS_CODE = "TRIP_JOINED";
    private static final String JOIN_SUCCESS_MESSAGE = "여행방에 참여했습니다.";
    private static final String LIST_SUCCESS_CODE = "TRIP_LIST_RETRIEVED";
    private static final String LIST_SUCCESS_MESSAGE = "참여 중인 여행방 목록을 조회했습니다.";
    private static final String DETAIL_SUCCESS_CODE = "TRIP_RETRIEVED";
    private static final String DETAIL_SUCCESS_MESSAGE = "여행방을 조회했습니다.";

    private final TripService tripService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TripCreateResponse> createTrip(
            Authentication authentication,
            @Valid @RequestBody TripCreateRequest request
    ) {
        TripCreateResponse response = tripService.createTrip(
                authentication.getName(),
                request
        );

        return ApiResponse.success(
                SUCCESS_CODE,
                SUCCESS_MESSAGE,
                response
        );
    }

    @PostMapping("/join")
    public ApiResponse<TripJoinResponse> joinTrip(
            Authentication authentication,
            @Valid @RequestBody TripJoinRequest request
    ) {
        TripJoinResponse response = tripService.joinTrip(
                authentication.getName(),
                request
        );

        return ApiResponse.success(
                JOIN_SUCCESS_CODE,
                JOIN_SUCCESS_MESSAGE,
                response
        );
    }

    @GetMapping
    public ApiResponse<TripListResponse> getTrips(
            Authentication authentication,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size
    ) {
        TripListResponse response = tripService.getTrips(
                authentication.getName(),
                cursor,
                size
        );

        return ApiResponse.success(
                LIST_SUCCESS_CODE,
                LIST_SUCCESS_MESSAGE,
                response
        );
    }

    @GetMapping("/{tripId}")
    public ApiResponse<TripDetailResponse> getTripDetail(
            Authentication authentication,
            @PathVariable Long tripId
    ) {
        TripDetailResponse response = tripService.getTripDetail(
                authentication.getName(),
                tripId
        );

        return ApiResponse.success(
                DETAIL_SUCCESS_CODE,
                DETAIL_SUCCESS_MESSAGE,
                response
        );
    }
}
