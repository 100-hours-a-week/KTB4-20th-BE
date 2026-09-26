package com.planit.schedule.repository;
import com.planit.schedule.domain.ScheduleLeg;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ScheduleLegRepository extends JpaRepository<ScheduleLeg, Long> {
    List<ScheduleLeg> findByDayIdOrderByLegOrderAsc(Long dayId);
}
