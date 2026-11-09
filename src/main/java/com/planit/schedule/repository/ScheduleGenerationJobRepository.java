package com.planit.schedule.repository;

import com.planit.schedule.domain.ScheduleGenerationJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleGenerationJobRepository extends JpaRepository<ScheduleGenerationJob, Long> {
}
