package com.planit.trip.service;

import com.planit.auth.token.SecureTokenGenerator;
import com.planit.auth.token.TokenHasher;
import com.planit.domain.*;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.image.config.ImageProperties;
import com.planit.repository.SubRegionRepository;
import com.planit.repository.TripInvitationRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.trip.dto.TripCreateRequest;
import com.planit.trip.dto.TripCreateResponse;
import com.planit.trip.dto.TripDetailResponse;
import com.planit.trip.dto.TripJoinRequest;
import com.planit.trip.dto.TripJoinResponse;
import com.planit.trip.dto.TripListResponse;
import com.planit.trip.pagination.TripListCursorCodec;
import com.planit.trip.pagination.TripListCursorCodec.Cursor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final TripListCursorCodec tripListCursorCodec;
    private final ImageProperties imageProperties;

    private static final int MAX_TRIP_LIST_SIZE = 10;

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

    @Override
    @Transactional
    public TripJoinResponse joinTrip(
            String userPublicId,
            TripJoinRequest request
    ) {
        User user = findActiveUser(userPublicId);

        String tokenHash = tokenHasher.sha256(
                request.invitationToken()
        );
        TripInvitation invitation = tripInvitationRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND
                ));

        Trip trip = findTripForUpdate(invitation);
        validateJoinConditions(trip, user);

        TripMember member = TripMember.createMember(trip, user);
        tripMemberRepository.save(member);

        return new TripJoinResponse(
                trip.getId().toString()
        );
    }

    @Override
    public TripDetailResponse getTripDetail(
            String userPublicId,
            Long tripId
    ) {
        User user = findActiveUser(userPublicId);
        Trip trip = findActiveTrip(tripId);
        TripMember currentMember = tripMemberRepository
                .findByTripAndUserAndLeftAtIsNull(trip, user)
                .filter(member -> member.getActiveSlot() != null
                        && member.getActiveSlot() == 1)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_MEMBER_REQUIRED
                ));
        List<TripMember> activeMembers =
                tripMemberRepository.findActiveMembersByTrip(trip);

        SubRegion subRegion = trip.getSubRegion();
        BroadRegion broadRegion = subRegion.getBroadRegion();
        List<TripDetailResponse.Member> members = activeMembers.stream()
                .map(member -> new TripDetailResponse.Member(
                        member.getUser().getPublicId(),
                        member.getUser().getUsername(),
                        imageProperties.defaultProfileUrl().toString(),
                        member.getRole()
                ))
                .toList();

        return new TripDetailResponse(
                trip.getId().toString(),
                trip.getName(),
                new TripDetailResponse.Region(
                        subRegion.getId().toString(),
                        broadRegion.getCode(),
                        broadRegion.getName(),
                        subRegion.getCode(),
                        subRegion.getName()
                ),
                trip.getStartDate(),
                trip.getEndDate(),
                trip.getCapacity(),
                members.size(),
                currentMember.getRole(),
                trip.getSurveyDeadlineAt()
                        .atZone(SEOUL_ZONE)
                        .toOffsetDateTime(),
                trip.getCreatedAt()
                        .atZone(SEOUL_ZONE)
                        .toOffsetDateTime(),
                members
        );
    }

    @Override
    public TripListResponse getTrips(
            String userPublicId,
            String encodedCursor,
            int size
    ) {
        validateTripListSize(size);
        User user = findActiveUser(userPublicId);

        Cursor cursor = encodedCursor == null
                ? null
                : tripListCursorCodec.decode(encodedCursor);
        LocalDate referenceDate = cursor == null
                ? LocalDate.now(SEOUL_ZONE)
                : cursor.referenceDate();

        List<TripMember> memberships = findTripMemberships(
                user,
                referenceDate,
                cursor,
                size
        );
        boolean hasNext = memberships.size() > size;
        List<TripMember> pageMemberships = memberships.subList(
                0,
                Math.min(size, memberships.size())
        );

        if (pageMemberships.isEmpty()) {
            return new TripListResponse(List.of(), null, false);
        }

        List<Long> tripIds = pageMemberships.stream()
                .map(member -> member.getTrip().getId())
                .toList();
        Map<Long, List<TripMember>> membersByTripId =
                tripMemberRepository.findActiveMembersByTripIds(tripIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                member -> member.getTrip().getId()
                        ));
        Set<Long> confirmedScheduleTripIds = new HashSet<>(
                tripRepository.findIdsWithActiveConfirmedSchedule(tripIds)
        );

        List<TripListResponse.TripSummary> trips = pageMemberships.stream()
                .map(member -> toTripSummary(
                        member.getTrip(),
                        membersByTripId.getOrDefault(
                                member.getTrip().getId(),
                                List.of()
                        ),
                        confirmedScheduleTripIds.contains(
                                member.getTrip().getId()
                        ),
                        referenceDate
                ))
                .toList();

        return new TripListResponse(
                trips,
                hasNext
                        ? encodeNextCursor(
                                pageMemberships.getLast(),
                                referenceDate
                        )
                        : null,
                hasNext
        );
    }

    private List<TripMember> findTripMemberships(
            User user,
            LocalDate referenceDate,
            Cursor cursor,
            int size
    ) {
        PageRequest pageRequest = PageRequest.of(0, size + 1);

        if (cursor == null) {
            return tripMemberRepository.findActiveTripMemberships(
                    user,
                    referenceDate,
                    pageRequest
            );
        }

        return tripMemberRepository.findActiveTripMembershipsAfter(
                user,
                referenceDate,
                cursor.startDate().isBefore(referenceDate) ? 1 : 0,
                cursor.startDate(),
                pageRequest
        );
    }

    private TripListResponse.TripSummary toTripSummary(
            Trip trip,
            List<TripMember> members,
            boolean hasConfirmedSchedule,
            LocalDate referenceDate
    ) {
        List<TripListResponse.MemberSummary> memberResponses = members.stream()
                .map(member -> new TripListResponse.MemberSummary(
                        member.getUser().getUsername(),
                        imageProperties.defaultProfileUrl().toString()
                ))
                .toList();

        return new TripListResponse.TripSummary(
                trip.getId().toString(),
                trip.getName(),
                trip.getStartDate(),
                TripProgressStatus.resolve(
                        trip.getStartDate(),
                        hasConfirmedSchedule,
                        referenceDate
                ),
                memberResponses.size(),
                memberResponses
        );
    }

    private String encodeNextCursor(
            TripMember lastMembership,
            LocalDate referenceDate
    ) {
        Trip trip = lastMembership.getTrip();
        return tripListCursorCodec.encode(new Cursor(
                referenceDate,
                trip.getStartDate()
        ));
    }

    private void validateTripListSize(int size) {
        if (size < 1 || size > MAX_TRIP_LIST_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private Trip findActiveTrip(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_NOT_FOUND
                ));
        if (trip.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.TRIP_NOT_FOUND);
        }
        return trip;
    }

    private Trip findTripForUpdate(TripInvitation invitation) {
        return tripRepository
                .findByIdForUpdate(invitation.getTrip().getId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND
                ));
    }

    private void validateJoinConditions(Trip trip, User user) {
        if (trip.getDeletedAt() != null) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }

        LocalDate today = LocalDate.now(SEOUL_ZONE);
        if (trip.getEndDate().isBefore(today)) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND
            );
        }

        if (tripMemberRepository
                .existsByTripAndUserAndLeftAtIsNull(trip, user)) {
            throw new BusinessException(
                    ErrorCode.TRIP_ALREADY_JOINED
            );
        }

        long activeMemberCount = tripMemberRepository
                .countByTripAndLeftAtIsNull(trip);
        if (activeMemberCount >= trip.getCapacity()) {
            throw new BusinessException(
                    ErrorCode.TRIP_CAPACITY_EXCEEDED
            );
        }

        long overlappingTripCount = tripMemberRepository
                .countActiveTripsOverlapping(
                        user,
                        trip.getStartDate(),
                        trip.getEndDate()
                );
        if (overlappingTripCount > 0) {
            throw new BusinessException(
                    ErrorCode.TRIP_DATE_CONFLICT
            );
        }
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
