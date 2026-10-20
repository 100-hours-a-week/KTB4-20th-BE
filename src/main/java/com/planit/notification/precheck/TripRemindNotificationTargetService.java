package com.planit.notification.precheck;

import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.TripMemberRole;
import com.planit.notification.domain.NotificationType;
import com.planit.notification.domain.WeatherCondition;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        Map<Long, String> hostNameByTripId = new HashMap<>();
        members.stream()
                .filter(TripMember::isCurrentHost)
                .forEach(member -> hostNameByTripId.putIfAbsent(
                        member.getTrip().getId(),
                        member.getUser().getUsername()
                ));
        Map<Long, WeatherIssue> issueByTripId = new HashMap<>();
        trips.forEach(trip -> issueByTripId.put(
                trip.getId(),
                resolveIssue(trip, cache)
        ));

        return members.stream()
                .map(member -> {
                    Trip trip = member.getTrip();
                    WeatherIssue issue = issueByTripId.get(trip.getId());
                    return new TripRemindNotificationTarget(
                            trip,
                            member.getUser(),
                            member.isCurrentHost()
                                    ? TripMemberRole.HOST
                                    : TripMemberRole.MEMBER,
                            issue.hasIssue()
                                    ? NotificationType.TRIP_WEATHER_ALERT
                                    : NotificationType.TRIP_D1,
                            issue.condition(),
                            hostNameByTripId.get(trip.getId())
                    );
                })
                .toList();
    }

    private WeatherIssue resolveIssue(
            Trip trip,
            TripRemindPrecheckCache cache
    ) {
        List<TripRemindPrecheckCache.RegionForecast> issues =
                cache.forecasts().stream()
                        .filter(forecast -> !forecast.date()
                                .isBefore(trip.getStartDate()))
                        .filter(forecast -> !forecast.date()
                                .isAfter(trip.getEndDate()))
                        .flatMap(forecast -> forecast.regions().stream())
                        .filter(region -> region.hasIssue()
                                && region.region().equals(
                                        trip.getRegion().getCode()
                                ))
                        .toList();
        if (issues.isEmpty()) {
            return new WeatherIssue(false, null);
        }

        List<String> details = issues.stream()
                .flatMap(region -> region.reasons().stream())
                .map(TripRemindPrecheckCache.Reason::detail)
                .toList();
        return new WeatherIssue(
                true,
                WeatherCondition.mostSevere(details).orElse(null)
        );
    }

    private record WeatherIssue(
            boolean hasIssue,
            WeatherCondition condition
    ) {
    }
}
