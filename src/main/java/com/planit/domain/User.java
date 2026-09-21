package com.planit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "image_file_id", nullable = false)
    private ImageFile imageFile;

    @Column(name = "public_id", columnDefinition = "BINARY(16)")
    private UUID publicId;

    @Column(name = "username", length = 20)
    private String username;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "age_range", length = 10)
    private String ageRange;

    @Column(name = "birth_year", length = 10)
    private String birthYear;

    @Column(name = "birth_day", length = 10)
    private String birthDay;

    @Column(name = "birthday_type", length = 10)
    private String birthdayType;

    @Column(name = "gender", length = 10)
    private String gender;

    protected User() {
    }

    public User(
            ImageFile imageFile,
            UUID publicId,
            String username
    ) {
        this.imageFile = imageFile;
        this.publicId = publicId;
        this.username = username;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public ImageFile getImageFile() {
        return imageFile;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getUsername() {
        return username;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public String getAgeRange() {
        return ageRange;
    }

    public String getBirthYear() {
        return birthYear;
    }

    public String getBirthDay() {
        return birthDay;
    }

    public String getBirthdayType() {
        return birthdayType;
    }

    public String getGender() {
        return gender;
    }
}