package com.planit.schedule.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name = "schedule_days")
public class ScheduleDay {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false) private Schedule schedule;
    @Column(name = "day_number", nullable = false) private byte dayNumber;
    @Column(name = "schedule_date", nullable = false) private LocalDate scheduleDate;
    protected ScheduleDay() {}
    public ScheduleDay(Schedule schedule, LocalDate date) {
        this.schedule = schedule; this.dayNumber = 1; this.scheduleDate = date;
    }
    public Long getId() { return id; }
    public byte getDayNumber() { return dayNumber; }
    public LocalDate getScheduleDate() { return scheduleDate; }
}
