package com.planit.repository;

import com.planit.domain.ImageFile;
import com.planit.domain.ImagePurpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImageFileRepository extends JpaRepository<ImageFile, Long> {

    Optional<ImageFile> findByImagePurposeAndDeletedAtIsNull(
            ImagePurpose imagePurpose
    );
}