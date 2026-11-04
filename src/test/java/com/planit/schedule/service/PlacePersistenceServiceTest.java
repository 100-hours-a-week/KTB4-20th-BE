package com.planit.schedule.service;

import com.planit.schedule.ai.RecommendedPlace;
import com.planit.schedule.domain.Place;
import com.planit.schedule.repository.PlaceRepository;
import com.planit.schedule.route.PlaceCategoryGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlacePersistenceServiceTest {

    private PlaceRepository placeRepository;
    private PlaceWriteService placeWriteService;
    private PlacePersistenceService service;

    @BeforeEach
    void setUp() {
        placeRepository = mock(PlaceRepository.class);
        placeWriteService = mock(PlaceWriteService.class);
        service = new PlacePersistenceService(
                placeRepository,
                placeWriteService
        );
    }

    @DisplayName("후보에 반복된 장소는 한 번만 조회하고 저장한다")
    @Test
    void deduplicatesRecommendedPlaces() {
        RecommendedPlace place = recommendation("google-1", "장소 1");
        when(placeRepository.findByRegion_IdAndGooglePlaceIdIn(
                10L,
                Set.of("google-1")
        )).thenReturn(List.of());
        when(placeWriteService.saveChangedAndMissingAndFindIds(
                org.mockito.ArgumentMatchers.eq(10L),
                any()
        )).thenReturn(Map.of("google-1", 1L));

        Map<String, Long> result = service.upsertAndFindIds(
                10L,
                List.of(place, place)
        );

        assertThat(result).containsEntry("google-1", 1L);
        verify(placeWriteService).saveChangedAndMissingAndFindIds(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.argThat(places ->
                        places.size() == 1
                                && places.getFirst().googlePlaceId()
                                .equals("google-1")
                )
        );
    }

    @DisplayName("모든 장소 정보가 같으면 쓰기 트랜잭션을 실행하지 않는다")
    @Test
    void skipsWriteWhenAllPlacesAreUnchanged() {
        Place savedPlace = mock(Place.class);
        when(savedPlace.getId()).thenReturn(1L);
        when(savedPlace.getGooglePlaceId()).thenReturn("google-1");
        when(savedPlace.hasSameDetails(any())).thenReturn(true);
        when(placeRepository.findByRegion_IdAndGooglePlaceIdIn(
                10L,
                Set.of("google-1")
        )).thenReturn(List.of(savedPlace));

        Map<String, Long> result = service.upsertAndFindIds(
                10L,
                List.of(recommendation("google-1", "장소 1"))
        );

        assertThat(result).containsEntry("google-1", 1L);
        verify(placeWriteService, never())
                .saveChangedAndMissingAndFindIds(any(Long.class), any());
    }

    private RecommendedPlace recommendation(String id, String name) {
        return new RecommendedPlace(
                id,
                name,
                35.0,
                129.0,
                "tourist_attraction",
                PlaceCategoryGroup.TOURISM_CULTURE,
                null,
                List.of(),
                List.of("HISTORY_CULTURE")
        );
    }
}
