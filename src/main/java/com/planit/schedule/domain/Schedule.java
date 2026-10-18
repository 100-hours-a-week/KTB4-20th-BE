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
    @Column(name = "preference_score") private Integer preferenceScore;
    @Column(length = 500) private String summary;
    @Column(name = "confirmed_at") private LocalDateTime confirmedAt;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;

    protected Schedule() {}
    public Schedule(Trip trip, LocalDateTime now) {
        this.trip = trip; this.strategy = "SHORTEST"; this.status = "ACTIVE";
        this.activeConfirmedSlot = (byte) 1; this.summary = "이동거리를 줄인 일정";
        this.confirmedAt = now; this.createdAt = now;
    }
    public Schedule(
            Trip trip,
            ScheduleStrategy strategy,
            Integer preferenceScore,
            LocalDateTime now
    ) {
        this.trip = trip;
        this.strategy = strategy.name();
        this.status = "DRAFT";
        this.preferenceScore = preferenceScore;
        this.summary = switch (strategy) {
            case SHORTEST -> "이동거리를 줄인 일정";
            case PREFERENCE -> "그룹 취향을 우선한 일정";
            case BALANCED -> "이동거리와 그룹 취향을 균형 있게 반영한 일정";
        };
        this.createdAt = now;
    }
    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public String getStrategy() { return strategy; }
    public String getStatus() { return status; }
    public Byte getActiveConfirmedSlot() { return activeConfirmedSlot; }
    public Integer getPreferenceScore() { return preferenceScore; }
    public String getSummary() { return summary; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
