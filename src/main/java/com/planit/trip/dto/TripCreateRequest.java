package com.planit.trip.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TripCreateRequest(

        @NotBlank
        @Size(min = 1, max = 12)
        @Pattern(regexp = "^[가-힣A-Za-z0-9 ]+$")
        String name,

        @NotNull
        @Positive
        Long regionId,

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate,

        @Min(2)
        @Max(8)
        Integer capacity,

        LocalDate surveyDeadlineDate
) {
}
