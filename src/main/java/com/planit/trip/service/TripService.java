package com.planit.trip.service;

import com.planit.trip.dto.TripCreateRequest;
import com.planit.trip.dto.TripCreateResponse;

public interface TripService {

    TripCreateResponse createTrip(
            String userPublicId,
            TripCreateRequest request
    );
}
