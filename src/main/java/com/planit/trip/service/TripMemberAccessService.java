package com.planit.trip.service;

import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.repository.TripMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripMemberAccessService {

    private final TripMemberRepository tripMemberRepository;

    public TripMember findActiveHost(
            Trip trip,
            User user,
            ErrorCode nonHostErrorCode
    ) {
        TripMember member = tripMemberRepository
                .findByTripAndUserAndLeftAtIsNull(trip, user)
                .filter(TripMember::isActive)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_MEMBER_REQUIRED
                ));
        if (!member.isCurrentHost()) {
            throw new BusinessException(nonHostErrorCode);
        }
        return member;
    }
}
