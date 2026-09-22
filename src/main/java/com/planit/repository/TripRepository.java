package com.planit.repository;

import com.planit.domain.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository
        extends JpaRepository<Trip, Long> {
}