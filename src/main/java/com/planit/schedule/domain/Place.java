package com.planit.schedule.domain;

import com.planit.domain.Region;
import com.planit.schedule.ai.RecommendedPlace;
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
    @Column(length = 255) private String address;
    @Column(name = "road_address", length = 255) private String roadAddress;
    @Column(nullable = false, precision = 10, scale = 7) private BigDecimal longitude;
    @Column(nullable = false, precision = 10, scale = 7) private BigDecimal latitude;
    @Column(length = 50) private String phone;
    @Column(name = "place_url", length = 2083) private String placeUrl;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    protected Place() {}

    public Place(Region region, RecommendedPlace source, LocalDateTime now) {
        this.region = region;
        this.googlePlaceId = source.googlePlaceId();
        update(source, now);
        this.createdAt = now;
    }

    public void update(RecommendedPlace source, LocalDateTime now) {
        this.name = source.name();
        this.categoryName = source.categoryName();
        this.longitude = BigDecimal.valueOf(source.longitude());
        this.latitude = BigDecimal.valueOf(source.latitude());
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public String getGooglePlaceId() { return googlePlaceId; }
    public String getName() { return name; }
    public String getCategoryName() { return categoryName; }
    public String getAddress() { return address; }
    public String getRoadAddress() { return roadAddress; }
    public BigDecimal getLongitude() { return longitude; }
    public BigDecimal getLatitude() { return latitude; }
}
