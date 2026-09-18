package com.planit.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "image_files")
public class ImageFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_key", length = 1024)
    private String imageKey;

    @Column(name = "thumbnail_key", length = 1024)
    private String thumbnailKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "image_purpose", length = 20)
    private ImagePurpose imagePurpose;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "size_bytes")
    private Integer sizeBytes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "storage_deleted_at")
    private LocalDateTime storageDeletedAt;

    protected ImageFile() {
    }
}