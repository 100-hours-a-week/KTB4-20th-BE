package com.planit.trip.service;

import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.schedule.service.SchedulePersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 여행방 자동 삭제 규칙을 한곳에서 적용한다. 나가기·회원 탈퇴로 마지막 한 명만 남는 경우와
 * 여행 전날 23:50까지 일정이 확정되지 않은 경우가 같은 규칙을 쓴다.
 * 호출자는 여행 행 잠금을 잡은 트랜잭션 안에서 호출해야 한다.
 */
@Component
@RequiredArgsConstructor
public class TripRoomDeleter {

    private final SchedulePersistenceService schedulePersistenceService;

    /**
     * 남은 활성 멤버십을 같은 시각으로 종료하고 여행방을 소프트 삭제한 뒤,
     * 후보·확정 일정과 하위 Day·방문 장소·이동 구간을 함께 정리한다.
     */
    public void delete(
            Trip trip,
            List<TripMember> activeMembers,
            LocalDateTime deletedAt
    ) {
        activeMembers.forEach(member -> member.leave(deletedAt));
        trip.delete(deletedAt);
        schedulePersistenceService.deleteAllForTrip(trip.getId());
    }
}
