package com.planit.schedule.service;

import com.planit.schedule.domain.Place;
import com.planit.schedule.domain.PlaceDetails;
import com.planit.schedule.repository.PlaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceWriteServiceTest {

    @DisplayName("변경된 장소만 갱신하고 없는 장소를 일괄 삽입한다")
    @Test
    void updatesChangedAndInsertsMissingPlaces() {
        PlaceRepository repository = mock(PlaceRepository.class);
        Place existing = mock(Place.class);
        Place inserted = mock(Place.class);
        PlaceDetails changed = details("google-1", "변경 장소");
        PlaceDetails missing = details("google-2", "신규 장소");
        when(existing.getGooglePlaceId()).thenReturn("google-1");
        when(existing.getId()).thenReturn(1L);
        when(existing.hasSameDetails(changed)).thenReturn(false);
        when(inserted.getGooglePlaceId()).thenReturn("google-2");
        when(inserted.getId()).thenReturn(2L);
        when(repository.findByRegion_IdAndGooglePlaceIdIn(
                10L,
                List.of("google-1", "google-2")
        )).thenReturn(
                List.of(existing),
                List.of(existing, inserted)
        );
        PlaceWriteService service = new PlaceWriteService(repository);

        Map<String, Long> result = service
                .saveChangedAndMissingAndFindIds(
                        10L,
                        List.of(changed, missing)
                );

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
                "google-1", 1L,
                "google-2", 2L
        ));
        verify(existing).update(any(), any());
        verify(repository).insertMissingAll(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.argThat(places ->
                        places.equals(List.of(missing))
                ),
                any()
        );
        verify(repository).flush();
    }

    private PlaceDetails details(String id, String name) {
        return new PlaceDetails(
                id,
                name,
                null,
                null,
                null,
                BigDecimal.valueOf(129.0),
                BigDecimal.valueOf(35.0),
                null,
                null
        );
    }
}
