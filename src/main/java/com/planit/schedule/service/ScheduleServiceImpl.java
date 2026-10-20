package com.planit.schedule.service;

import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.schedule.domain.Schedule;
import com.planit.schedule.domain.ScheduleDay;
import com.planit.schedule.domain.ScheduleLeg;
import com.planit.schedule.domain.ScheduleVisit;
import com.planit.schedule.dto.ScheduleDetailResponse;
import com.planit.schedule.repository.ScheduleDayRepository;
import com.planit.schedule.repository.ScheduleLegRepository;
import com.planit.schedule.repository.ScheduleRepository;
import com.planit.schedule.repository.ScheduleVisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleServiceImpl implements ScheduleService {

    private static final byte ACTIVE_CONFIRMED_SLOT = 1;
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleDayRepository scheduleDayRepository;
    private final ScheduleVisitRepository scheduleVisitRepository;
    private final ScheduleLegRepository scheduleLegRepository;

    @Override
    public ScheduleDetailResponse getSchedule(
            String userPublicId,
            Long tripId
    ) {
        User user = findActiveUser(userPublicId);
        Trip trip = findActiveTrip(tripId);
        findActiveMember(trip, user);

        Schedule schedule = scheduleRepository
                .findByTripIdAndActiveConfirmedSlot(tripId, ACTIVE_CONFIRMED_SLOT)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACTIVE_SCHEDULE_NOT_FOUND
                ));

        List<ScheduleDetailResponse.Day> days = scheduleDayRepository
                .findByScheduleIdOrderByDayNumberAsc(schedule.getId())
                .stream()
                .map(this::toDayResponse)
                .toList();

        int totalDistanceMeters = days.stream()
                .mapToInt(ScheduleDetailResponse.Day::totalDistanceMeters)
                .sum();

        return new ScheduleDetailResponse(
                trip.getId().toString(),
                schedule.getId().toString(),
                schedule.getStrategy(),
                schedule.getStatus(),
                false,
                totalDistanceMeters,
                schedule.getCreatedAt().atZone(SEOUL_ZONE).toOffsetDateTime(),
                days
        );
    }

    private ScheduleDetailResponse.Day toDayResponse(ScheduleDay day) {
        List<ScheduleVisit> visits = scheduleVisitRepository
                .findByDayIdOrderByVisitOrderAsc(day.getId());
        List<ScheduleLeg> legs = scheduleLegRepository
                .findByDayIdOrderByLegOrderAsc(day.getId());

        return new ScheduleDetailResponse.Day(
                day.getId().toString(),
                day.getDayNumber(),
                day.getScheduleDate(),
                legs.stream().mapToInt(ScheduleLeg::getDistanceMeters).sum(),
                visits.stream().map(this::toStopResponse).toList(),
                legs.stream().map(this::toLegResponse).toList()
        );
    }

    private ScheduleDetailResponse.Stop toStopResponse(ScheduleVisit visit) {
        return new ScheduleDetailResponse.Stop(
                visit.getId().toString(),
                visit.getPlace().getId().toString(),
                visit.getVisitOrder(),
                visit.getPlaceNameSnapshot(),
                visit.getPlace().getCategoryName(),
                visit.getAddressSnapshot(),
                visit.getPlace().getRoadAddress(),
                visit.getPlace().getLongitude(),
                visit.getPlace().getLatitude(),
                visit.getSelectionReason()
        );
    }

    private ScheduleDetailResponse.Leg toLegResponse(ScheduleLeg leg) {
        return new ScheduleDetailResponse.Leg(
                leg.getId().toString(),
                leg.getFromVisit().getId().toString(),
                leg.getToVisit().getId().toString(),
                leg.getLegOrder(),
                leg.getDistanceMeters()
        );
    }

    private TripMember findActiveMember(Trip trip, User user) {
        return tripMemberRepository
                .findByTripAndUserAndLeftAtIsNull(trip, user)
                .filter(member -> member.getActiveSlot() != null
                        && member.getActiveSlot() == 1)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_MEMBER_REQUIRED
                ));
    }

    private Trip findActiveTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_NOT_FOUND
                ));
        if (trip.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        return trip;
    }

    private User findActiveUser(String userPublicId) {
        UUID publicId = UUID.fromString(userPublicId);

        return userRepository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED
                ));
    }
}
