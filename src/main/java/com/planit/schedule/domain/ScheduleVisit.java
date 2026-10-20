package com.planit.schedule.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

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
    @Column(name = "removed_at") private LocalDateTime removedAt;
    @Column(length = 20) private String source;
    @Column(name = "selection_reason", length = 500) private String selectionReason;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_preferences_json", columnDefinition = "json")
    private List<String> matchedPreferences;
    protected ScheduleVisit() {}
    public ScheduleVisit(ScheduleDay day, Place place, int order, String reason, LocalDateTime now) {
        this(day, place, order, reason, List.of(), now);
    }
    public ScheduleVisit(
            ScheduleDay day,
            Place place,
            int order,
            String reason,
            List<String> matchedPreferences,
            LocalDateTime now
    ) {
        this.day = day; this.place = place; this.visitOrder = (short) order;
        this.placeNameSnapshot = place.getName(); this.addressSnapshot = place.getAddress();
        this.status = "ACTIVE"; this.source = "AI"; this.selectionReason = reason;
        this.matchedPreferences = List.copyOf(matchedPreferences);
        this.createdAt = now; this.updatedAt = now;
    }

    public static ScheduleVisit createManual(
            ScheduleDay day,
            Place place,
            int order,
            LocalDateTime now
    ) {
        ScheduleVisit visit = new ScheduleVisit();
        visit.day = day;
        visit.place = place;
        visit.visitOrder = (short) order;
        visit.placeNameSnapshot = place.getName();
        visit.addressSnapshot = place.getAddress();
        visit.status = "ACTIVE";
        visit.source = "MANUAL";
        visit.selectionReason = "사용자가 직접 추가한 장소입니다.";
        visit.matchedPreferences = List.of();
        visit.createdAt = now;
        visit.updatedAt = now;
        return visit;
    }
    public Long getId() { return id; }
    public ScheduleDay getDay() { return day; }
    public Place getPlace() { return place; }
    public short getVisitOrder() { return visitOrder; }
    public String getPlaceNameSnapshot() { return placeNameSnapshot; }
    public String getAddressSnapshot() { return addressSnapshot; }
    public String getStatus() { return status; }
    public LocalDateTime getRemovedAt() { return removedAt; }
    public String getSource() { return source; }
    public String getSelectionReason() { return selectionReason; }
    public List<String> getMatchedPreferences() {
        return matchedPreferences == null ? List.of() : List.copyOf(matchedPreferences);
    }

    public void remove(LocalDateTime now) {
        this.status = "REMOVED";
        this.removedAt = now;
        this.updatedAt = now;
    }

    public void moveForward() {
        this.visitOrder--;
    }

    public void moveBackward() {
        this.visitOrder++;
    }
}
