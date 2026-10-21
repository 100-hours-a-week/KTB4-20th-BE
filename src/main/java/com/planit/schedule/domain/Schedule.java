package com.planit.schedule.domain;

import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedules")
public class Schedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false) private Trip trip;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by_member_id")
    private TripMember confirmedByMember;
    @Column(nullable = false, length = 30) private String strategy;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "active_confirmed_slot") private Byte activeConfirmedSlot;
    @Column(name = "preference_count") private Integer preferenceCount;
    @Column(name = "discovery_count") private Integer discoveryCount;
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
            Integer preferenceCount,
            Integer discoveryCount,
            LocalDateTime now
    ) {
        this.trip = trip;
        this.strategy = strategy.name();
        this.status = "DRAFT";
        this.preferenceCount = preferenceCount;
        this.discoveryCount = discoveryCount;
        this.summary = switch (strategy) {
            case SHORTEST -> "이동거리를 줄인 일정";
            case PREFERENCE -> "그룹 취향을 우선한 일정";
            case DIVERSITY -> "새로운 장소를 다양하게 발견하는 일정";
        };
        this.createdAt = now;
    }
    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public String getStrategy() { return strategy; }
    public String getStatus() { return status; }
    public Byte getActiveConfirmedSlot() { return activeConfirmedSlot; }
    public Integer getPreferenceCount() { return preferenceCount; }
    public Integer getDiscoveryCount() { return discoveryCount; }
    public String getSummary() { return summary; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public TripMember getConfirmedByMember() { return confirmedByMember; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public boolean isActive() {
        return "ACTIVE".equals(status) && activeConfirmedSlot != null;
    }

    public void confirm(TripMember member, LocalDateTime now) {
        if (isActive()) {
            return;
        }
        this.status = "ACTIVE";
        this.activeConfirmedSlot = (byte) 1;
        this.confirmedByMember = member;
        this.confirmedAt = now;
    }

    public void supersede() {
        this.status = "SUPERSEDED";
        this.activeConfirmedSlot = null;
    }
}
