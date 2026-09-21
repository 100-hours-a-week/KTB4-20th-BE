package com.planit.user.dto;

import java.util.UUID;

public record CurrentUserResponse(
        UUID publicId,
        String userName,
        String profileImageUrl
) {
}