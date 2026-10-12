package com.planit.photomission.domain;

import com.planit.domain.Trip;
import com.planit.schedule.domain.ScheduleDay;
import com.planit.schedule.domain.ScheduleVisit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "missions")
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_day_id", nullable = false)
    private ScheduleDay scheduleDay;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_visit_id", nullable = false)
    private ScheduleVisit scheduleVisit;

    @Column(name = "mission_order", nullable = false)
    private byte missionOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "mission_scope", nullable = false, length = 20)
    private MissionScope scope;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "primary_category", length = 50)
    private String primaryCategory;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Mission() {
    }

    public static Mission create(
            ScheduleVisit scheduleVisit,
            int missionOrder,
            MissionScope scope,
            String description,
            String primaryCategory,
            LocalDateTime now
    ) {
        if (scheduleVisit == null) {
            throw new IllegalArgumentException("미션 대상 방문 장소는 필수입니다");
        }
        ScheduleDay scheduleDay = scheduleVisit.getDay();
        if (scheduleDay == null
                || scheduleDay.getSchedule() == null
                || scheduleDay.getSchedule().getTrip() == null) {
            throw new IllegalArgumentException(
                    "미션 대상 방문 장소의 여행 일정은 필수입니다"
            );
        }
        Trip trip = scheduleDay.getSchedule().getTrip();

        Mission mission = new Mission();
        mission.trip = trip;
        mission.scheduleDay = scheduleDay;
        mission.scheduleVisit = scheduleVisit;
        mission.missionOrder = (byte) missionOrder;
        mission.scope = scope;
        mission.description = description;
        mission.primaryCategory = primaryCategory;
        mission.expiresAt = trip.getEndDate()
                .plusDays(1)
                .atStartOfDay();
        mission.createdAt = now;
        return mission;
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isPersonal() {
        return scope == MissionScope.PERSONAL;
    }

    public boolean isGroup() {
        return scope == MissionScope.GROUP;
    }

    public Long getId() {
        return id;
    }

    public Trip getTrip() {
        return trip;
    }

    public ScheduleDay getScheduleDay() {
        return scheduleDay;
    }

    public ScheduleVisit getScheduleVisit() {
        return scheduleVisit;
    }

    public byte getMissionOrder() {
        return missionOrder;
    }

    public MissionScope getScope() {
        return scope;
    }

    public String getDescription() {
        return description;
    }

    public String getPrimaryCategory() {
        return primaryCategory;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}
