package com.planit.schedule.repository;

import com.planit.schedule.domain.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PlaceRepository extends JpaRepository<Place, Long> {
    Optional<Place> findByRegion_IdAndGooglePlaceId(
            Long regionId,
            String googlePlaceId
    );
}
