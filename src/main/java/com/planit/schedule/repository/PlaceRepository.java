package com.planit.schedule.repository;

import com.planit.schedule.domain.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlaceRepository
        extends JpaRepository<Place, Long>, PlaceRepositoryCustom {

    Optional<Place> findByRegion_IdAndGooglePlaceId(
            Long regionId,
            String googlePlaceId
    );

    List<Place> findByRegion_IdAndGooglePlaceIdIn(
            Long regionId,
            Collection<String> googlePlaceIds
    );
}
