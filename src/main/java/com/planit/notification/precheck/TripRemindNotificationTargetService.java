package com.planit.notification.precheck;

import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.notification.domain.NotificationType;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TripRemindNotificationTargetService {

    private final TripRemindPrecheckStore precheckStore;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;

    public List<TripRemindNotificationTarget> findTargets(
            LocalDate targetDate
    ) {
        Objects.requireNonNull(targetDate, "여행 시작 대상일은 필수입니다");

        TripRemindPrecheckCache cache = precheckStore.find(targetDate)
                .map(TripRemindPrecheckSnapshot::cache)
                .filter(value -> value.status()
                        == TripRemindNotificationWorkflowStatus.PRECHECKED)
                .orElseThrow(() -> new IllegalStateException(
                        "PRECHECKED 상태의 사전 점검 결과가 없습니다."
                ));

        List<Trip> trips = tripRepository.findActiveTripsStartingOn(targetDate);
        if (trips.isEmpty()) {
            return List.of();
        }

        List<Long> tripIds = trips.stream().map(Trip::getId).toList();
        List<TripMember> members =
                tripMemberRepository.findActiveMembersByTripIds(tripIds);

        return members.stream()
                .map(member -> new TripRemindNotificationTarget(
                        member.getTrip(),
                        member.getUser(),
                        resolveType(member.getTrip(), cache)
                ))
                .toList();
    }

    private NotificationType resolveType(
            Trip trip,
            TripRemindPrecheckCache cache
    ) {
        boolean hasIssue = cache.forecasts().stream()
                .filter(forecast -> !forecast.date().isBefore(trip.getStartDate()))
                .filter(forecast -> !forecast.date().isAfter(trip.getEndDate()))
                .flatMap(forecast -> forecast.regions().stream())
                .anyMatch(region -> region.hasIssue()
                        && region.region().equals(trip.getRegion().getCode()));

        return hasIssue
                ? NotificationType.TRIP_WEATHER_ALERT
                : NotificationType.TRIP_D1;
    }
}
