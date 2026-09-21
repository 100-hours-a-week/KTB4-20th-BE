package com.planit.user.service;

import com.planit.user.dto.CurrentUserResponse;

public interface UserService {

    CurrentUserResponse getCurrentUser(String userPublicId);

    void withdraw(String userPublicId);
}