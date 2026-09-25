package com.planit.schedule.repository;
import com.planit.schedule.domain.ScheduleDay;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ScheduleDayRepository extends JpaRepository<ScheduleDay, Long> {}
