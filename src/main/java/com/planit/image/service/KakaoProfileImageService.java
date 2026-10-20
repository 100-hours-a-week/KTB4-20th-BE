package com.planit.image.service;

import com.planit.auth.kakao.KakaoUserResponse;
import com.planit.domain.ImageFile;
import java.util.Optional;
import java.util.UUID;

public interface KakaoProfileImageService {

    Optional<ImageFile> importIfPresent(
            UUID userPublicId,
            KakaoUserResponse kakaoUser
    );
}
