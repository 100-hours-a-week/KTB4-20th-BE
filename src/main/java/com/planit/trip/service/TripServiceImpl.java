package com.planit.trip.service;

import com.planit.auth.token.SecureTokenGenerator;
import com.planit.auth.token.TokenHasher;
import com.planit.domain.*;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.repository.SubRegionRepository;
import com.planit.repository.TripInvitationRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.trip.dto.TripCreateRequest;
import com.planit.trip.dto.TripCreateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TripServiceImpl implements TripService {

    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final SubRegionRepository subRegionRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripInvitationRepository tripInvitationRepository;
    private final SecureTokenGenerator secureTokenGenerator;
    private final TokenHasher tokenHasher;

    @Override
    @Transactional
    public TripCreateResponse createTrip(
            String userPublicId,
            TripCreateRequest request
    ) {
        User user = findActiveUser(userPublicId);
        SubRegion subRegion = findSubRegion(request.subRegionId());

        LocalDate today = LocalDate.now(SEOUL_ZONE);

        validateStartDate(request.startDate(), today);
        validateNoDateConflict(user, request.startDate());

        LocalDateTime surveyDeadlineAt = resolveSurveyDeadline(
                request.startDate(),
                request.surveyDeadlineDate(),
                today
        );

        Trip trip = new Trip(
                subRegion,
                request.name(),
                request.startDate(),
                request.capacity().byteValue(),
                surveyDeadlineAt
        );

        Trip savedTrip = tripRepository.save(trip);

        TripMember host = TripMember.createHost(savedTrip, user);
        tripMemberRepository.save(host);

        String invitationToken = secureTokenGenerator.generate();

        String invitationTokenHash = tokenHasher.sha256(invitationToken);

        TripInvitation invitation =
                new TripInvitation(
                        savedTrip,
                        invitationTokenHash
                );

        tripInvitationRepository.save(invitation);

        return new TripCreateResponse(
                savedTrip.getId().toString(),
                invitationToken
        );
    }

    private User findActiveUser(String userPublicId) {
        UUID publicId = UUID.fromString(userPublicId);

        return userRepository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.AUTHENTICATION_REQUIRED
                        )
                );
    }

    private SubRegion findSubRegion(Long subRegionId) {
        return subRegionRepository
                .findById(subRegionId)
                .orElseThrow(() ->
                        new BusinessException(
                                ErrorCode.RESOURCE_NOT_FOUND
                        )
                );
    }

    private void validateStartDate(
            LocalDate startDate,
            LocalDate today
    ) {
        if (startDate.isBefore(today)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST
            );
        }
    }

    private void validateNoDateConflict(
            User user,
            LocalDate startDate
    ) {
        long overlappingTripCount = tripMemberRepository
                .countActiveTripsOverlapping(
                        user,
                        startDate,
                        startDate
                );

        if (overlappingTripCount > 0) {
            throw new BusinessException(
                    ErrorCode.TRIP_DATE_CONFLICT
            );
        }
    }

    private LocalDateTime resolveSurveyDeadline(
            LocalDate startDate,
            LocalDate selectedDate,
            LocalDate today
    ) {
        if (startDate.equals(today)) {
            return startDate.atTime(12, 0);
        }

        LocalDate deadlineDate = selectedDate != null
                ? selectedDate
                : startDate.minusDays(1);

        if (deadlineDate.isBefore(today)
                || !deadlineDate.isBefore(startDate)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST
            );
        }

        return deadlineDate.atTime(
                23,
                59,
                59,
                999_999_000
        );
    }
}
