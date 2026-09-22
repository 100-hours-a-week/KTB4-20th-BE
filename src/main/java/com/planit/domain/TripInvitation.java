package com.planit.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "trip_invitations")
public class TripInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    protected TripInvitation() {
    }

    public TripInvitation(
            Trip trip,
            String tokenHash
    ) {
        this.trip = trip;
        this.tokenHash = tokenHash;
    }

    public Long getId() {
        return id;
    }

    public Trip getTrip() {
        return trip;
    }

    public String getTokenHash() {
        return tokenHash;
    }
}