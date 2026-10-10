package com.planit.photomission.domain;

import com.planit.domain.ImageFile;
import com.planit.domain.TripMember;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "mission_photos",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_mission_photos_idempotency_key",
                        columnNames = "idempotency_key"
                ),
                @UniqueConstraint(
                        name = "uk_mission_photos_participation_active",
                        columnNames = {
                                "mission_participation_id",
                                "active_slot"
                        }
                ),
                @UniqueConstraint(
                        name = "uk_mission_photos_mission_representative",
                        columnNames = {"mission_id", "representative_slot"}
                )
        }
)
public class MissionPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_participation_id")
    private MissionParticipation participation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_member_id", nullable = false)
    private TripMember submittedBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "image_file_id", nullable = false)
    private ImageFile imageFile;

    @Column(name = "idempotency_key", nullable = false, columnDefinition = "BINARY(16)")
    private UUID idempotencyKey;

    @Column(name = "photo_latitude", precision = 10, scale = 7)
    private BigDecimal photoLatitude;

    @Column(name = "photo_longitude", precision = 10, scale = 7)
    private BigDecimal photoLongitude;

    @Column(name = "active_slot")
    private Byte activeSlot;

    @Column(name = "representative_slot")
    private Byte representativeSlot;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "replaced_at")
    private LocalDateTime replacedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected MissionPhoto() {
    }

    public static MissionPhoto personal(
            MissionParticipation participation,
            ImageFile imageFile,
            UUID idempotencyKey,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDateTime submittedAt
    ) {
        MissionPhoto photo = base(
                participation.getMission(),
                participation.getTripMember(),
                imageFile,
                idempotencyKey,
                latitude,
                longitude,
                submittedAt
        );
        photo.participation = participation;
        photo.activeSlot = (byte) 1;
        return photo;
    }

    public static MissionPhoto group(
            Mission mission,
            TripMember submittedBy,
            ImageFile imageFile,
            UUID idempotencyKey,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDateTime submittedAt
    ) {
        if (!mission.isGroup()) {
            throw new IllegalArgumentException(
                    "단체 사진은 단체 미션에만 제출할 수 있습니다"
            );
        }

        return base(
                mission,
                submittedBy,
                imageFile,
                idempotencyKey,
                latitude,
                longitude,
                submittedAt
        );
    }

    private static MissionPhoto base(
            Mission mission,
            TripMember submittedBy,
            ImageFile imageFile,
            UUID idempotencyKey,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDateTime submittedAt
    ) {
        MissionPhoto photo = new MissionPhoto();
        photo.mission = mission;
        photo.submittedBy = submittedBy;
        photo.imageFile = imageFile;
        photo.idempotencyKey = idempotencyKey;
        photo.photoLatitude = latitude;
        photo.photoLongitude = longitude;
        photo.submittedAt = submittedAt;
        return photo;
    }

    public void markRepresentative() {
        if (!mission.isGroup()) {
            throw new IllegalStateException(
                    "단체 미션 사진만 대표 사진으로 지정할 수 있습니다"
            );
        }
        representativeSlot = (byte) 1;
    }

    public void replace(LocalDateTime replacedAt) {
        activeSlot = null;
        this.replacedAt = replacedAt;
    }

    public void delete(LocalDateTime deletedAt) {
        activeSlot = null;
        representativeSlot = null;
        this.deletedAt = deletedAt;
    }

    public Long getId() {
        return id;
    }

    public MissionParticipation getParticipation() {
        return participation;
    }

    public Mission getMission() {
        return mission;
    }

    public TripMember getSubmittedBy() {
        return submittedBy;
    }

    public ImageFile getImageFile() {
        return imageFile;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public BigDecimal getPhotoLatitude() {
        return photoLatitude;
    }

    public BigDecimal getPhotoLongitude() {
        return photoLongitude;
    }

    public Byte getActiveSlot() {
        return activeSlot;
    }

    public Byte getRepresentativeSlot() {
        return representativeSlot;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public LocalDateTime getReplacedAt() {
        return replacedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
