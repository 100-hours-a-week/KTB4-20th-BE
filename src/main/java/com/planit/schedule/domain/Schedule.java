package com.planit.schedule.domain;

import com.planit.domain.Trip;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedules")
public class Schedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false) private Trip trip;
    @Column(nullable = false, length = 30) private String strategy;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "active_confirmed_slot") private Byte activeConfirmedSlot;
    @Column(length = 500) private String summary;
    @Column(name = "confirmed_at") private LocalDateTime confirmedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected Schedule() {}
    public Schedule(Trip trip, LocalDateTime now) {
        this.trip = trip; this.strategy = "SHORTEST"; this.status = "ACTIVE";
        this.activeConfirmedSlot = (byte) 1; this.summary = "이동거리를 줄인 일정";
        this.confirmedAt = now; this.createdAt = now;
    }
    public Long getId() { return id; }
}
