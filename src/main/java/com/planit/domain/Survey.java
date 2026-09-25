package com.planit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "surveys")
public class Survey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "trip_member_id", nullable = false)
    private TripMember tripMember;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    protected Survey() {
    }

    public Survey(TripMember tripMember, LocalDateTime now) {
        this.tripMember = tripMember;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void submit(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
        this.updatedAt = submittedAt;
    }

    public Long getId() {
        return id;
    }

    public TripMember getTripMember() {
        return tripMember;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
}
