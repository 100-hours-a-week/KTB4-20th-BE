package com.planit.schedule.repository;

import com.planit.schedule.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    boolean existsByTripIdAndActiveConfirmedSlot(Long tripId, Byte activeConfirmedSlot);
}
