package com.planit.schedule.service;

import com.planit.global.error.BusinessException;
import com.planit.schedule.domain.Place;
import com.planit.schedule.domain.PlaceDetails;
import com.planit.schedule.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.planit.global.error.ErrorCode.INVALID_AI_PLACE_RESULT;

@Service
@RequiredArgsConstructor
public class PlaceWriteService {

    private final PlaceRepository placeRepository;

    @Transactional
    public Map<String, Long> saveChangedAndMissingAndFindIds(
            long regionId,
            List<PlaceDetails> places
    ) {
        List<String> googlePlaceIds = places.stream()
                .map(PlaceDetails::googlePlaceId)
                .toList();
        Map<String, Place> existingById = placeRepository
                .findByRegion_IdAndGooglePlaceIdIn(regionId, googlePlaceIds)
                .stream()
                .collect(Collectors.toMap(
                        Place::getGooglePlaceId,
                        Function.identity(),
                        (first, ignored) -> first
                ));
        LocalDateTime now = LocalDateTime.now();

        places.stream()
                .filter(details -> existingById.containsKey(
                        details.googlePlaceId()
                ))
                .filter(details -> !existingById.get(details.googlePlaceId())
                        .hasSameDetails(details))
                .forEach(details -> existingById.get(details.googlePlaceId())
                        .update(details, now));
        placeRepository.flush();

        List<PlaceDetails> missingPlaces = places.stream()
                .filter(details -> !existingById.containsKey(
                        details.googlePlaceId()
                ))
                .toList();
        placeRepository.insertMissingAll(regionId, missingPlaces, now);

        List<Place> savedPlaces = placeRepository
                .findByRegion_IdAndGooglePlaceIdIn(regionId, googlePlaceIds);
        if (savedPlaces.size() != googlePlaceIds.size()) {
            throw new BusinessException(INVALID_AI_PLACE_RESULT);
        }
        return toPlaceIds(savedPlaces);
    }

    static Map<String, Long> toPlaceIds(List<Place> places) {
        return places.stream().collect(Collectors.toUnmodifiableMap(
                Place::getGooglePlaceId,
                Place::getId
        ));
    }
}
