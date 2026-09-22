package com.planit.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TripTest {

    @Test
    void createsTrip() {
        SubRegion subRegion = new SubRegion();
        LocalDate startDate = LocalDate.of(2026, 9, 23);

        LocalDateTime surveyDeadlineAt = LocalDateTime.of(
                2026,
                9,
                22,
                23,
                59,
                59,
                999_999_000
        );

        Trip trip = new Trip(
                subRegion,
                "부산 맛집 여행",
                startDate,
                (byte) 4,
                surveyDeadlineAt
        );

        assertThat(trip.getSubRegion()).isSameAs(subRegion);
        assertThat(trip.getName()).isEqualTo("부산 맛집 여행");
        assertThat(trip.getStartDate()).isEqualTo(startDate);
        assertThat(trip.getEndDate()).isEqualTo(startDate);
        assertThat(trip.getCapacity()).isEqualTo((byte) 4);
        assertThat(trip.getSurveyDeadlineAt())
                .isEqualTo(surveyDeadlineAt);
        assertThat(trip.getCreatedAt()).isNotNull();
        assertThat(trip.getDeletedAt()).isNull();
    }
}
