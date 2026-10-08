package com.planit.photomission.domain;

import com.planit.domain.Trip;
import com.planit.schedule.domain.ScheduleDay;
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

    @Column(name = "mission_generation_job_id", nullable = false)
    private Long missionGenerationJobId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_day_id", nullable = false)
    private ScheduleDay scheduleDay;

    @Column(name = "mission_order", nullable = false)
    private byte missionOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "mission_scope", nullable = false, length = 20)
    private MissionScope scope;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @Column(name = "conditions_json", nullable = false, columnDefinition = "json")
    private String conditionsJson;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Mission() {
    }

    public static Mission create(
            Long missionGenerationJobId,
            Trip trip,
            ScheduleDay scheduleDay,
            int missionOrder,
            MissionScope scope,
            String title,
            String description,
            String conditionsJson,
            LocalDateTime now
    ) {
        Mission mission = new Mission();
        mission.missionGenerationJobId = missionGenerationJobId;
        mission.trip = trip;
        mission.scheduleDay = scheduleDay;
        mission.missionOrder = (byte) missionOrder;
        mission.scope = scope;
        mission.title = title;
        mission.description = description;
        mission.conditionsJson = conditionsJson;
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

    public Long getMissionGenerationJobId() {
        return missionGenerationJobId;
    }

    public Trip getTrip() {
        return trip;
    }

    public ScheduleDay getScheduleDay() {
        return scheduleDay;
    }

    public byte getMissionOrder() {
        return missionOrder;
    }

    public MissionScope getScope() {
        return scope;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getConditionsJson() {
        return conditionsJson;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}
