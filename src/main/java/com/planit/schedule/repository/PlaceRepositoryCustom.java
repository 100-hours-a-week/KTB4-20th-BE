package com.planit.schedule.repository;

import com.planit.schedule.domain.PlaceDetails;

import java.time.LocalDateTime;
import java.util.List;

public interface PlaceRepositoryCustom {

    void insertMissingAll(
            Long regionId,
            List<PlaceDetails> places,
            LocalDateTime now
    );
}
