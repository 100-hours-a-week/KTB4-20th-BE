package com.planit.trip.exception;

import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.trip.dto.TripConflictResponse;

public class TripDateConflictException extends BusinessException {

    private final TripConflictResponse conflict;

    public TripDateConflictException(TripConflictResponse conflict) {
        super(ErrorCode.TRIP_DATE_CONFLICT);
        this.conflict = conflict;
    }

    public TripConflictResponse getConflict() {
        return conflict;
    }
}
