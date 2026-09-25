package com.planit.schedule.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "schedule_visits")
public class ScheduleVisit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_day_id", nullable = false) private ScheduleDay day;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id", nullable = false) private Place place;
    @Column(name = "visit_order", nullable = false) private short visitOrder;
    @Column(name = "place_name_snapshot", nullable = false, length = 200) private String placeNameSnapshot;
    @Column(name = "address_snapshot", length = 255) private String addressSnapshot;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @Column(length = 20) private String source;
    @Column(name = "selection_reason", length = 500) private String selectionReason;
    protected ScheduleVisit() {}
    public ScheduleVisit(ScheduleDay day, Place place, int order, String reason, LocalDateTime now) {
        this.day = day; this.place = place; this.visitOrder = (short) order;
        this.placeNameSnapshot = place.getName(); this.addressSnapshot = place.getAddress();
        this.status = "ACTIVE"; this.source = "AI"; this.selectionReason = reason;
        this.createdAt = now; this.updatedAt = now;
    }
    public Long getId() { return id; }
    public Place getPlace() { return place; }
}
