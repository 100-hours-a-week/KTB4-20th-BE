package com.planit.schedule.repository;
import com.planit.schedule.domain.ScheduleLeg;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ScheduleLegRepository extends JpaRepository<ScheduleLeg, Long> {}
