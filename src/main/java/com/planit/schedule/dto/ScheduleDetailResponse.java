package com.planit.schedule.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** SCHEDULE_API_SPEC.md 3장(생성 일정 조회) 계약과 같은 모양이다. V1은 항상 editable=false다. */
public record ScheduleDetailResponse(
        String tripId,
        String scheduleId,
        String strategy,
        String status,
        boolean editable,
        int totalDistanceMeters,
        OffsetDateTime createdAt,
        List<Day> days
) {
    public ScheduleDetailResponse {
        days = List.copyOf(days);
    }

    public record Day(
            String dayId,
            int dayNumber,
            LocalDate date,
            int totalDistanceMeters,
            List<Stop> stops,
            List<Leg> legs
    ) {
        public Day {
            stops = List.copyOf(stops);
            legs = List.copyOf(legs);
        }
    }

    public record Stop(
            String stopId,
            String placeId,
            int order,
            String name,
            String categoryName,
            String address,
            String roadAddress,
            BigDecimal longitude,
            BigDecimal latitude,
            String selectionReason
    ) {
    }

    public record Leg(
            String legId,
            String fromStopId,
            String toStopId,
            int order,
            int distanceMeters
    ) {
    }
}
