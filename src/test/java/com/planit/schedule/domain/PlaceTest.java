package com.planit.schedule.domain;

import com.planit.domain.Region;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PlaceTest {

    @DisplayName("공통 장소 정보로 생성하고 문자열 값을 정규화한다")
    @Test
    void createsFromPlaceDetails() {
        Place place = new Place(
                mock(Region.class),
                details(
                        " google-place-id ",
                        " 장소명 ",
                        " 카페 ",
                        " 주소 ",
                        "010-0000-0000"
                ),
                LocalDateTime.now()
        );

        assertThat(place.getGooglePlaceId()).isEqualTo("google-place-id");
        assertThat(place.getName()).isEqualTo("장소명");
        assertThat(place.getCategoryName()).isEqualTo("카페");
        assertThat(place.getAddress()).isEqualTo("주소");
        assertThat(place.getPhone()).isEqualTo("010-0000-0000");
    }

    @DisplayName("새 입력에 없는 선택 정보는 기존 장소 값으로 유지한다")
    @Test
    void preservesOptionalDetailsWhenUpdateDoesNotProvideThem() {
        Place place = new Place(
                mock(Region.class),
                details(
                        "google-place-id",
                        "기존 장소",
                        "카페",
                        "기존 주소",
                        "010-0000-0000"
                ),
                LocalDateTime.now().minusDays(1)
        );

        place.update(
                details(
                        "google-place-id",
                        "AI 갱신 장소",
                        null,
                        null,
                        null
                ),
                LocalDateTime.now()
        );

        assertThat(place.getName()).isEqualTo("AI 갱신 장소");
        assertThat(place.getCategoryName()).isEqualTo("카페");
        assertThat(place.getAddress()).isEqualTo("기존 주소");
        assertThat(place.getPhone()).isEqualTo("010-0000-0000");
    }

    private PlaceDetails details(
            String googlePlaceId,
            String name,
            String categoryName,
            String address,
            String phone
    ) {
        return new PlaceDetails(
                googlePlaceId,
                name,
                categoryName,
                address,
                null,
                BigDecimal.valueOf(129.15),
                BigDecimal.valueOf(35.15),
                phone,
                null
        );
    }
}
