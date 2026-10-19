package com.planit.trip.service;

import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 여행 시작 전날 23:50까지 일정을 확정하지 않은 여행방을 자동 삭제한다.
 * 일정 후보를 생성하지 않은 여행과 후보만 생성하고 확정하지 않은 여행을 모두 대상으로 한다.
 */
@Service
@RequiredArgsConstructor
public class UnconfirmedTripDeletionService {

    private static final byte ACTIVE_CONFIRMED_SLOT = 1;

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final ScheduleRepository scheduleRepository;
    private final TripRoomDeleter tripRoomDeleter;

    /**
     * startDate에 시작하고 삭제되지 않은 여행 중 ACTIVE 확정 일정이 없는 여행 ID를 반환한다.
     * 잠금 없이 조회한 후보이므로 실제 삭제 전 deleteIfUnconfirmed에서 다시 검사한다.
     */
    @Transactional(readOnly = true)
    public List<Long> findTargetTripIds(LocalDate startDate) {
        List<Long> tripIds = tripRepository.findActiveTripIdsStartingOn(
                startDate
        );
        if (tripIds.isEmpty()) {
            return List.of();
        }
        Set<Long> confirmedTripIds = new HashSet<>(
                tripRepository.findIdsWithActiveConfirmedSchedule(tripIds)
        );
        return tripIds.stream()
                .filter(tripId -> !confirmedTripIds.contains(tripId))
                .toList();
    }

    /**
     * 여행 행을 잠근 뒤 삭제 조건을 다시 확인하고 여행방을 소프트 삭제한다.
     * 확정 요청과 같은 행 잠금으로 직렬화되므로, 먼저 커밋된 확정은 삭제를 막고
     * 삭제가 먼저 커밋되면 이후 확정 요청은 삭제된 여행으로 거부된다.
     * 이미 삭제됐거나 확정된 여행은 건너뛰므로 반복 실행해도 안전하다.
     *
     * @return 이번 호출로 여행방을 삭제했으면 true
     */
    @Transactional
    public boolean deleteIfUnconfirmed(
            Long tripId,
            LocalDate startDate,
            LocalDateTime deletedAt
    ) {
        Trip trip = tripRepository.findByIdForUpdate(tripId).orElse(null);
        if (trip == null
                || trip.getDeletedAt() != null
                || !startDate.equals(trip.getStartDate())
                || scheduleRepository.existsByTripIdAndActiveConfirmedSlot(
                        tripId,
                        ACTIVE_CONFIRMED_SLOT
                )) {
            return false;
        }

        List<TripMember> activeMembers = tripMemberRepository
                .findActiveMembersByTripIncludingWithdrawn(trip);
        tripRoomDeleter.delete(trip, activeMembers, deletedAt);
        return true;
    }
}
