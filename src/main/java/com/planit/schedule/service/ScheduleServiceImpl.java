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
import com.planit.schedule.dto.ScheduleStopDeleteResponse;
import com.planit.schedule.repository.ScheduleDayRepository;
import com.planit.schedule.repository.ScheduleLegRepository;
import com.planit.schedule.repository.ScheduleRepository;
import com.planit.schedule.repository.ScheduleVisitRepository;
import com.planit.trip.service.TripMemberAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.planit.schedule.route.HaversineDistanceCalculator.distanceMeters;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleServiceImpl implements ScheduleService {

    private static final byte ACTIVE_CONFIRMED_SLOT = 1;
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripMemberAccessService tripMemberAccessService;
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

    @Override
    @Transactional
    public ScheduleStopDeleteResponse deleteStop(
            String userPublicId,
            Long tripId,
            Long stopId
    ) {
        User user = findActiveUser(userPublicId);
        Trip trip = tripRepository.findByIdForUpdate(tripId)
                .filter(value -> value.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_NOT_FOUND
                ));
        tripMemberAccessService.findActiveHost(
                trip,
                user,
                ErrorCode.TRIP_HOST_REQUIRED
        );
        if (!LocalDate.now(SEOUL_ZONE).isBefore(trip.getStartDate())) {
            throw new BusinessException(ErrorCode.SCHEDULE_CHANGE_NOT_ALLOWED);
        }

        Schedule schedule = scheduleRepository
                .findByTripIdAndActiveConfirmedSlot(
                        tripId,
                        ACTIVE_CONFIRMED_SLOT
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACTIVE_SCHEDULE_NOT_FOUND
                ));
        ScheduleVisit target = scheduleVisitRepository
                .findWithDayAndScheduleById(stopId)
                .filter(visit -> visit.getDay().getSchedule().getId()
                        .equals(schedule.getId()))
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SCHEDULE_STOP_NOT_FOUND
                ));
        ScheduleDay day = target.getDay();

        if ("REMOVED".equals(target.getStatus())) {
            return deleteResponse(schedule, target, day);
        }
        if (scheduleVisitRepository.countByDay_Schedule_IdAndStatus(
                schedule.getId(),
                "ACTIVE"
        ) <= 3) {
            throw new BusinessException(
                    ErrorCode.SCHEDULE_MINIMUM_STOPS_REQUIRED
            );
        }

        List<ScheduleVisit> visits = scheduleVisitRepository
                .findByDayIdAndStatusOrderByVisitOrderAsc(
                        day.getId(),
                        "ACTIVE"
                );
        int targetIndex = findVisitIndex(visits, target.getId());
        if (targetIndex < 0) {
            throw new BusinessException(ErrorCode.SCHEDULE_STOP_NOT_FOUND);
        }

        updateLegsForDeletion(day, visits, targetIndex);
        target.remove(LocalDateTime.now());
        visits.subList(targetIndex + 1, visits.size())
                .forEach(ScheduleVisit::moveForward);

        return deleteResponse(schedule, target, day);
    }

    private int findVisitIndex(List<ScheduleVisit> visits, Long visitId) {
        for (int index = 0; index < visits.size(); index++) {
            if (visits.get(index).getId().equals(visitId)) {
                return index;
            }
        }
        return -1;
    }

    private void updateLegsForDeletion(
            ScheduleDay day,
            List<ScheduleVisit> visits,
            int targetIndex
    ) {
        ScheduleVisit target = visits.get(targetIndex);
        List<ScheduleLeg> legs = scheduleLegRepository
                .findByDayIdOrderByLegOrderAsc(day.getId());
        List<ScheduleLeg> connectedLegs = legs.stream()
                .filter(leg -> leg.getFromVisit().getId().equals(target.getId())
                        || leg.getToVisit().getId().equals(target.getId()))
                .toList();
        scheduleLegRepository.deleteAll(connectedLegs);

        if (targetIndex > 0 && targetIndex < visits.size() - 1) {
            ScheduleVisit previous = visits.get(targetIndex - 1);
            ScheduleVisit next = visits.get(targetIndex + 1);
            scheduleLegRepository.save(new ScheduleLeg(
                    day,
                    previous,
                    next,
                    targetIndex,
                    distanceMeters(
                            previous.getPlace().getLatitude(),
                            previous.getPlace().getLongitude(),
                            next.getPlace().getLatitude(),
                            next.getPlace().getLongitude()
                    )
            ));
        }

        legs.stream()
                .filter(leg -> !connectedLegs.contains(leg))
                .filter(leg -> leg.getLegOrder() > targetIndex)
                .forEach(ScheduleLeg::moveForward);
    }

    private ScheduleStopDeleteResponse deleteResponse(
            Schedule schedule,
            ScheduleVisit deletedVisit,
            ScheduleDay day
    ) {
        return new ScheduleStopDeleteResponse(
                schedule.getId().toString(),
                deletedVisit.getId().toString(),
                toDayResponse(day)
        );
    }

    private ScheduleDetailResponse.Day toDayResponse(ScheduleDay day) {
        List<ScheduleVisit> visits = scheduleVisitRepository
                .findByDayIdAndStatusOrderByVisitOrderAsc(
                        day.getId(),
                        "ACTIVE"
                );
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
