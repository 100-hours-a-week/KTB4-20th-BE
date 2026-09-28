package com.planit.schedule.repository;

import com.planit.schedule.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    boolean existsByTripIdAndActiveConfirmedSlot(Long tripId, Byte activeConfirmedSlot);
    Optional<Schedule> findByTripIdAndActiveConfirmedSlot(Long tripId, Byte activeConfirmedSlot);
    List<Schedule> findByTripId(Long tripId);
}
