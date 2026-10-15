package com.planit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageFileTest {

    @DisplayName("카카오 프로필 이미지 메타데이터를 생성한다")
    @Test
    void createsProfileImage() {
        ImageFile imageFile = ImageFile.profile(
                "profiles/user-id/profile.jpg",
                "image/jpeg",
                1024
        );

        assertThat(imageFile.getImageKey())
                .isEqualTo("profiles/user-id/profile.jpg");
        assertThat(imageFile.getImagePurpose())
                .isEqualTo(ImagePurpose.PROFILE);
        assertThat(imageFile.getMimeType())
                .isEqualTo("image/jpeg");
        assertThat(imageFile.getSizeBytes())
                .isEqualTo(1024);
        assertThat(imageFile.getCreatedAt())
                .isNotNull();
    }

    @DisplayName("포토미션 이미지를 MISSION 용도로 생성한다")
    @Test
    void createsMissionImage() {
        ImageFile imageFile = ImageFile.mission(
                "missions/31/photo.jpg",
                "photo.jpg",
                "image/jpeg",
                1024
        );

        assertThat(imageFile.getImageKey())
                .isEqualTo("missions/31/photo.jpg");
        assertThat(imageFile.getImagePurpose())
                .isEqualTo(ImagePurpose.MISSION);
        assertThat(imageFile.getOriginalFilename())
                .isEqualTo("photo.jpg");
        assertThat(imageFile.getMimeType()).isEqualTo("image/jpeg");
        assertThat(imageFile.getSizeBytes()).isEqualTo(1024);
        assertThat(imageFile.getCreatedAt()).isNotNull();
    }
}
