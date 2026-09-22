package com.planit.repository;

import com.planit.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    boolean existsByPublicIdAndDeletedAtIsNull(UUID publicId);
}
