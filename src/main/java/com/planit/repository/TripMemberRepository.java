package com.planit.repository;

import com.planit.domain.TripMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripMemberRepository
        extends JpaRepository<TripMember, Long> {
}