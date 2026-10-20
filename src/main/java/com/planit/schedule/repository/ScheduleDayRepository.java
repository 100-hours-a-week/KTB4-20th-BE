package com.planit.schedule.repository;
import com.planit.schedule.domain.ScheduleDay;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ScheduleDayRepository extends JpaRepository<ScheduleDay, Long> {
    List<ScheduleDay> findByScheduleIdOrderByDayNumberAsc(Long scheduleId);
}
