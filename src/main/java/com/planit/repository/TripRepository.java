package com.planit.repository;

import com.planit.domain.Trip;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TripRepository
        extends JpaRepository<Trip, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT trip FROM Trip trip WHERE trip.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") Long id);
}
