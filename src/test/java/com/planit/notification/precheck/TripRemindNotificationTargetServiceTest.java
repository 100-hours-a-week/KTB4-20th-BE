package com.planit.notification.precheck;

import com.planit.domain.Region;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.notification.domain.NotificationType;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TripRemindNotificationTargetServiceTest {

    private final TripRemindPrecheckStore precheckStore =
            mock(TripRemindPrecheckStore.class);
    private final TripRepository tripRepository = mock(TripRepository.class);
    private final TripMemberRepository tripMemberRepository =
            mock(TripMemberRepository.class);
    private final TripRemindNotificationTargetService service =
            new TripRemindNotificationTargetService(
                    precheckStore,
                    tripRepository,
                    tripMemberRepository
            );

    @DisplayName("여행 기간의 지역별 점검 결과에 따라 참여자 알림 유형을 산출한다")
    @Test
    void findsTargetsByTripPeriodAndRegion() {
        LocalDate targetDate = LocalDate.of(2026, 10, 16);
        Trip normalTrip = trip(
                1L,
                "REGION-SEOUL",
                targetDate,
                targetDate.plusDays(1)
        );
        Trip issueTrip = trip(
                2L,
                "REGION-BUSAN",
                targetDate,
                targetDate.plusDays(2)
        );
        User normalRecipient = mock(User.class);
        User issueRecipient = mock(User.class);
        TripMember normalMember = member(normalTrip, normalRecipient);
        TripMember issueMember = member(issueTrip, issueRecipient);

        when(precheckStore.find(targetDate)).thenReturn(Optional.of(
                new TripRemindPrecheckSnapshot(cache(targetDate), 1_000L)
        ));
        when(tripRepository.findActiveTripsStartingOn(targetDate))
                .thenReturn(List.of(normalTrip, issueTrip));
        when(tripMemberRepository.findActiveMembersByTripIds(List.of(1L, 2L)))
                .thenReturn(List.of(normalMember, issueMember));

        List<TripRemindNotificationTarget> targets =
                service.findTargets(targetDate);

        assertThat(targets).containsExactly(
                new TripRemindNotificationTarget(
                        normalTrip,
                        normalRecipient,
                        NotificationType.TRIP_D1
                ),
                new TripRemindNotificationTarget(
                        issueTrip,
                        issueRecipient,
                        NotificationType.TRIP_WEATHER_ALERT
                )
        );
    }

    @DisplayName("대상 여행이 없으면 참여자를 조회하지 않는다")
    @Test
    void returnsEmptyWhenNoTripStartsOnTargetDate() {
        LocalDate targetDate = LocalDate.of(2026, 10, 16);
        when(precheckStore.find(targetDate)).thenReturn(Optional.of(
                new TripRemindPrecheckSnapshot(cache(targetDate), 1_000L)
        ));
        when(tripRepository.findActiveTripsStartingOn(targetDate))
                .thenReturn(List.of());

        assertThat(service.findTargets(targetDate)).isEmpty();
        verify(tripMemberRepository, never())
                .findActiveMembersByTripIds(List.of());
    }

    @DisplayName("PRECHECKED 결과가 없으면 알림 대상을 산출하지 않는다")
    @Test
    void rejectsMissingPrecheckedResult() {
        LocalDate targetDate = LocalDate.of(2026, 10, 16);
        when(precheckStore.find(targetDate)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findTargets(targetDate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PRECHECKED");
        verify(tripRepository, never()).findActiveTripsStartingOn(targetDate);
    }

    private Trip trip(
            Long id,
            String regionCode,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Trip trip = mock(Trip.class);
        Region region = mock(Region.class);
        when(trip.getId()).thenReturn(id);
        when(trip.getRegion()).thenReturn(region);
        when(trip.getStartDate()).thenReturn(startDate);
        when(trip.getEndDate()).thenReturn(endDate);
        when(region.getCode()).thenReturn(regionCode);
        return trip;
    }

    private TripMember member(Trip trip, User user) {
        TripMember member = mock(TripMember.class);
        when(member.getTrip()).thenReturn(trip);
        when(member.getUser()).thenReturn(user);
        return member;
    }

    private TripRemindPrecheckCache cache(LocalDate targetDate) {
        return new TripRemindPrecheckCache(
                Instant.parse("2026-10-15T01:00:00Z"),
                TripRemindNotificationWorkflowStatus.PRECHECKED,
                List.of(
                        new TripRemindPrecheckCache.Forecast(
                                targetDate,
                                List.of(
                                        region("REGION-SEOUL", false),
                                        region("REGION-BUSAN", false)
                                )
                        ),
                        new TripRemindPrecheckCache.Forecast(
                                targetDate.plusDays(2),
                                List.of(
                                        region("REGION-SEOUL", false),
                                        region("REGION-BUSAN", true)
                                )
                        )
                )
        );
    }

    private TripRemindPrecheckCache.RegionForecast region(
            String code,
            boolean hasIssue
    ) {
        return new TripRemindPrecheckCache.RegionForecast(
                code,
                hasIssue,
                List.of()
        );
    }
}
