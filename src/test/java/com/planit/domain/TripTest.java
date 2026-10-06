package com.planit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TripTest {

    @DisplayName("여행을 생성하면 여행 기간을 저장하고 삭제되지 않은 상태로 초기화한다")
    @Test
    void createsTrip() {
        Region region = new Region();
        LocalDate startDate = LocalDate.of(2026, 9, 23);
        LocalDate endDate = LocalDate.of(2026, 9, 25);

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
                region,
                "부산 맛집 여행",
                startDate,
                endDate,
                (byte) 4,
                surveyDeadlineAt
        );

        assertThat(trip.getRegion()).isSameAs(region);
        assertThat(trip.getName()).isEqualTo("부산 맛집 여행");
        assertThat(trip.getStartDate()).isEqualTo(startDate);
        assertThat(trip.getEndDate()).isEqualTo(endDate);
        assertThat(trip.getCapacity()).isEqualTo((byte) 4);
        assertThat(trip.getSurveyDeadlineAt())
                .isEqualTo(surveyDeadlineAt);
        assertThat(trip.getCreatedAt()).isNotNull();
        assertThat(trip.getDeletedAt()).isNull();
    }
}
