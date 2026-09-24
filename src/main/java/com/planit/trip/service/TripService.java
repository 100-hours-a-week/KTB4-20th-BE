package com.planit.trip.service;

import com.planit.trip.dto.TripCreateRequest;
import com.planit.trip.dto.TripCreateResponse;
import com.planit.trip.dto.TripJoinRequest;
import com.planit.trip.dto.TripJoinResponse;
import com.planit.trip.dto.TripListResponse;

public interface TripService {

    TripCreateResponse createTrip(
            String userPublicId,
            TripCreateRequest request
    );

    TripJoinResponse joinTrip(
            String userPublicId,
            TripJoinRequest request
    );

    TripListResponse getTrips(
            String userPublicId,
            String cursor,
            int size
    );
}
