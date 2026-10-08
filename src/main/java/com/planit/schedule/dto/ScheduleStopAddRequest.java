package com.planit.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ScheduleStopAddRequest(
        @NotNull @Positive Long scheduleDayId,
        @NotNull @Positive Integer position,
        @NotNull @Valid Place place
) {
    public record Place(
            @NotBlank @Size(max = 100) String googlePlaceId,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 255) String categoryName,
            @Size(max = 255) String address,
            @Size(max = 255) String roadAddress,
            @NotNull
            @DecimalMin(value = "-180.0")
            @DecimalMax(value = "180.0")
            BigDecimal longitude,
            @NotNull
            @DecimalMin(value = "-90.0")
            @DecimalMax(value = "90.0")
            BigDecimal latitude,
            @Size(max = 50) String phone,
            @Size(max = 2083) String placeUrl
    ) {
    }
}
