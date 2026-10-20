package com.planit.schedule.domain;

import java.math.BigDecimal;

public record PlaceDetails(
        String googlePlaceId,
        String name,
        String categoryName,
        String address,
        String roadAddress,
        BigDecimal longitude,
        BigDecimal latitude,
        String phone,
        String placeUrl
) {
}
