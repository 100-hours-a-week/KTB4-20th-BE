package com.planit.repository;

import com.planit.domain.Survey;
import com.planit.domain.TripMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SurveyRepository extends JpaRepository<Survey, Long> {

    Optional<Survey> findByTripMember(TripMember tripMember);

    List<Survey> findByTripMemberInAndSubmittedAtIsNotNull(
            List<TripMember> tripMembers
    );
}
