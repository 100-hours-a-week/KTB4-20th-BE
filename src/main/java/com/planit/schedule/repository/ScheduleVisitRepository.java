package com.planit.schedule.repository;
import com.planit.schedule.domain.ScheduleVisit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
public interface ScheduleVisitRepository extends JpaRepository<ScheduleVisit, Long> {
    @EntityGraph(attributePaths = "place")
    List<ScheduleVisit> findByDayIdOrderByVisitOrderAsc(Long dayId);

    @EntityGraph(attributePaths = {"place", "day", "day.schedule"})
    @Query("SELECT visit FROM ScheduleVisit visit WHERE visit.id = :id")
    Optional<ScheduleVisit> findWithDayAndScheduleById(@Param("id") Long id);

    @EntityGraph(attributePaths = "place")
    List<ScheduleVisit> findByDayIdAndStatusOrderByVisitOrderAsc(
            Long dayId,
            String status
    );

    long countByDay_Schedule_IdAndStatus(Long scheduleId, String status);
}
