package com.planit.schedule.domain;

import jakarta.persistence.*;

@Entity @Table(name = "schedule_legs")
public class ScheduleLeg {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_day_id", nullable = false) private ScheduleDay day;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_visit_id", nullable = false) private ScheduleVisit fromVisit;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_visit_id", nullable = false) private ScheduleVisit toVisit;
    @Column(name = "leg_order", nullable = false) private short legOrder;
    @Column(name = "distance_meters", nullable = false) private int distanceMeters;
    @Column(name = "required_time") private Integer requiredTime;
    protected ScheduleLeg() {}
    public ScheduleLeg(ScheduleDay day, ScheduleVisit from, ScheduleVisit to, int order, long distance) {
        this.day = day; this.fromVisit = from; this.toVisit = to;
        this.legOrder = (short) order; this.distanceMeters = Math.toIntExact(distance);
    }
    public Long getId() { return id; }
    public ScheduleVisit getFromVisit() { return fromVisit; }
    public ScheduleVisit getToVisit() { return toVisit; }
    public short getLegOrder() { return legOrder; }
    public int getDistanceMeters() { return distanceMeters; }
}
