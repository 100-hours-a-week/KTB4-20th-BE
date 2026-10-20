package com.planit.schedule.domain;

import com.planit.domain.Region;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "places")
public class Place {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)

    private Region region;
    @Column(name = "google_place_id", nullable = false, length = 100)
    private String googlePlaceId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "category_name", length = 255)
    private String categoryName;

    @Column(length = 255)
    private String address;

    @Column(name = "road_address", length = 255)
    private String roadAddress;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(length = 50)
    private String phone;

    @Column(name = "place_url", length = 2083)
    private String placeUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Place() {}

    public Place(Region region, PlaceDetails details, LocalDateTime now) {
        this.region = region;
        this.googlePlaceId = details.googlePlaceId().trim();
        this.createdAt = now;
        update(details, now);
    }

    public void update(PlaceDetails details, LocalDateTime now) {
        this.name = details.name().trim();
        this.categoryName = mergeOptional(this.categoryName, details.categoryName());
        this.address = mergeOptional(this.address, details.address());
        this.roadAddress = mergeOptional(this.roadAddress, details.roadAddress());
        this.longitude = details.longitude();
        this.latitude = details.latitude();
        this.phone = mergeOptional(this.phone, details.phone());
        this.placeUrl = mergeOptional(this.placeUrl, details.placeUrl());
        this.updatedAt = now;
    }

    private static String mergeOptional(String currentValue, String newValue) {
        if (newValue == null) {
            return currentValue;
        }
        if (newValue.isBlank()) {
            return null;
        }
        return newValue.trim();
    }

    public Long getId() { return id; }
    public String getGooglePlaceId() { return googlePlaceId; }
    public String getName() { return name; }
    public String getCategoryName() { return categoryName; }
    public String getAddress() { return address; }
    public String getRoadAddress() { return roadAddress; }
    public BigDecimal getLongitude() { return longitude; }
    public BigDecimal getLatitude() { return latitude; }
    public String getPhone() { return phone; }
    public String getPlaceUrl() { return placeUrl; }
}
