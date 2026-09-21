package com.planit.domain;

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
@Table(name = "trip_members")
public class TripMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private TripMemberRole role;

    @Column(name = "host_slot")
    private Byte hostSlot;

    @Column(name = "active_slot")
    private Byte activeSlot;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    protected TripMember() {
    }

    private TripMember(
            Trip trip,
            User user,
            TripMemberRole role,
            Byte hostSlot
    ) {
        this.trip = trip;
        this.user = user;
        this.role = role;
        this.hostSlot = hostSlot;
        this.activeSlot = (byte) 1;
        this.joinedAt = LocalDateTime.now();
    }

    public static TripMember createHost(
            Trip trip,
            User user
    ) {
        return new TripMember(
                trip,
                user,
                TripMemberRole.HOST,
                (byte) 1
        );
    }

    public static TripMember createMember(
            Trip trip,
            User user
    ) {
        return new TripMember(
                trip,
                user,
                TripMemberRole.MEMBER,
                null
        );
    }

    public Long getId() {
        return id;
    }

    public Trip getTrip() {
        return trip;
    }

    public User getUser() {
        return user;
    }

    public TripMemberRole getRole() {
        return role;
    }

    public Byte getHostSlot() {
        return hostSlot;
    }

    public Byte getActiveSlot() {
        return activeSlot;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public LocalDateTime getLeftAt() {
        return leftAt;
    }
}
