package com.planit.schedule.service;

import com.planit.schedule.ai.RecommendedPlace;
import com.planit.schedule.domain.Place;
import com.planit.schedule.domain.PlaceDetails;
import com.planit.schedule.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlacePersistenceService {

    private final PlaceRepository placeRepository;
    private final PlaceWriteService placeWriteService;

    public Map<String, Long> upsertAndFindIds(
            long regionId,
            Collection<RecommendedPlace> recommendations
    ) {
        Map<String, PlaceDetails> detailsById = recommendations.stream()
                .collect(Collectors.toMap(
                        RecommendedPlace::googlePlaceId,
                        PlacePersistenceService::toPlaceDetails,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        List<PlaceDetails> places = List.copyOf(detailsById.values());
        List<Place> savedPlaces = placeRepository
                .findByRegion_IdAndGooglePlaceIdIn(
                        regionId,
                        detailsById.keySet()
                );
        boolean allSaved = savedPlaces.size() == places.size()
                && savedPlaces.stream().allMatch(place -> place.hasSameDetails(
                        detailsById.get(place.getGooglePlaceId())
                ));
        if (allSaved) {
            return PlaceWriteService.toPlaceIds(savedPlaces);
        }
        return placeWriteService.saveChangedAndMissingAndFindIds(
                regionId,
                places
        );
    }

    private static PlaceDetails toPlaceDetails(
            RecommendedPlace recommendation
    ) {
        return new PlaceDetails(
                recommendation.googlePlaceId(),
                recommendation.name(),
                recommendation.categoryName(),
                null,
                null,
                BigDecimal.valueOf(recommendation.longitude()),
                BigDecimal.valueOf(recommendation.latitude()),
                null,
                null
        );
    }
}
