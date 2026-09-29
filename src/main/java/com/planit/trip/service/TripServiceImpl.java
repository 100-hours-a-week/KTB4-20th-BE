package com.planit.trip.service;

import com.planit.auth.config.AuthProperties;
import com.planit.auth.token.TokenHasher;
import com.planit.domain.*;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.image.config.ImageProperties;
import com.planit.repository.RegionRepository;
import com.planit.repository.TripInvitationRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.TripRepository;
import com.planit.repository.UserRepository;
import com.planit.schedule.service.SchedulePersistenceService;
import com.planit.trip.dto.TripCreateRequest;
import com.planit.trip.dto.TripCreateResponse;
import com.planit.trip.dto.TripDetailResponse;
import com.planit.trip.dto.TripJoinRequest;
import com.planit.trip.dto.TripJoinResponse;
import com.planit.trip.dto.TripInvitationPreviewResponse;
import com.planit.trip.dto.TripLeaveResponse;
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
    private final AuthProperties authProperties;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripInvitationRepository tripInvitationRepository;

    private final TokenHasher tokenHasher;
    private final TripListCursorCodec tripListCursorCodec;
    private final ImageProperties imageProperties;
    private final SchedulePersistenceService schedulePersistenceService;

    private static final int MAX_TRIP_LIST_SIZE = 10;
    private static final String WITHDRAWN_USER_NAME = "탈퇴한 사용자";

    @Override
    @Transactional
    public TripCreateResponse createTrip(
            String userPublicId,
            TripCreateRequest request
    ) {
        User user = findActiveUser(userPublicId);
        Region region = findRegion(request.regionId());

        LocalDate today = LocalDate.now(SEOUL_ZONE);

        validateStartDate(request.startDate(), today);
        validateNoDateConflict(user, request.startDate());

        LocalDateTime surveyDeadlineAt = resolveSurveyDeadline(
                request.startDate(),
                request.surveyDeadlineDate(),
                today
        );

        Trip trip = new Trip(
                region,
                request.name(),
                request.startDate(),
                request.capacity().byteValue(),
                surveyDeadlineAt
        );

        Trip savedTrip = tripRepository.save(trip);

        TripMember host = TripMember.createHost(savedTrip, user);
        tripMemberRepository.save(host);

        String invitationToken = generateInvitationToken(savedTrip.getId());

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
    public TripInvitationPreviewResponse getInvitationPreview(
            String userPublicId,
            String invitationToken
    ) {
        String tokenHash = tokenHasher.sha256(invitationToken);
        TripInvitation invitation = tripInvitationRepository
                .findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVITATION_NOT_FOUND
                ));
        Trip trip = invitation.getTrip();

        if (trip.getDeletedAt() != null) {
            throw new BusinessException(
                    ErrorCode.INVITATION_TRIP_DELETED
            );
        }

        LocalDate today = LocalDate.now(SEOUL_ZONE);
        if (trip.getEndDate().isBefore(today)) {
            throw new BusinessException(ErrorCode.INVITATION_EXPIRED);
        }

        LocalDateTime now = LocalDateTime.now(SEOUL_ZONE);
        if (!trip.getSurveyDeadlineAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.SURVEY_CLOSED);
        }

        if (userPublicId == null) {
            throw new BusinessException(
                    ErrorCode.AUTHENTICATION_REQUIRED
            );
        }
        User user = findActiveUser(userPublicId);

        List<TripMember> members =
                tripMemberRepository.findActiveMembersByTrip(trip);
        TripMember host = members.stream()
                .filter(member -> member.getRole() == TripMemberRole.HOST)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_SERVER_ERROR
                ));
        Region region = trip.getRegion();
        List<TripInvitationPreviewResponse.Member> memberResponses =
                members.stream()
                        .map(member ->
                                new TripInvitationPreviewResponse.Member(
                                        member.getUser().getUsername(),
                                        imageProperties.defaultProfileUrl()
                                                .toString()
                                ))
                        .toList();
        boolean alreadyJoined = tripMemberRepository
                .existsByTripAndUserAndLeftAtIsNull(trip, user);
        TripInvitationPreviewResponse.ConflictingTrip conflictingTrip =
                tripMemberRepository.findActiveTripsOverlappingExcept(
                                user,
                                trip.getStartDate(),
                                trip.getEndDate(),
                                trip.getId()
                        ).stream()
                        .findFirst()
                        .map(member -> new TripInvitationPreviewResponse
                                .ConflictingTrip(
                                        member.getTrip().getId().toString(),
                                        member.getTrip().getName()
                                ))
                        .orElse(null);

        return new TripInvitationPreviewResponse(
                new TripInvitationPreviewResponse.Trip(
                        trip.getId().toString(),
                        trip.getName(),
                        new TripInvitationPreviewResponse.Region(
                                region.getId().toString(),
                                region.getName()
                        ),
                        trip.getStartDate(),
                        trip.getEndDate(),
                        members.size(),
                        trip.getCapacity()
                ),
                new TripInvitationPreviewResponse.Inviter(
                        host.getUser().getPublicId(),
                        host.getUser().getUsername(),
                        imageProperties.defaultProfileUrl().toString()
                ),
                memberResponses,
                alreadyJoined,
                conflictingTrip
        );
    }

    @Override
    public TripDetailResponse getTripDetail(
            String userPublicId,
            Long tripId
    ) {
        User user = findActiveUser(userPublicId);
        Trip trip = findActiveTrip(tripId);
        TripMember currentMember = findActiveMember(trip, user);
        List<TripMember> activeMembers = tripMemberRepository
                .findActiveMembersByTripIncludingWithdrawn(trip);

        Region region = trip.getRegion();
        List<TripDetailResponse.Member> members = activeMembers.stream()
                .map(this::toMemberResponse)
                .toList();

        return new TripDetailResponse(
                trip.getId().toString(),
                trip.getName(),
                new TripDetailResponse.Region(
                        region.getId().toString(),
                        region.getCode(),
                        region.getName()
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
    @Transactional
    public TripLeaveResponse leaveTrip(
            String userPublicId,
            Long tripId
    ) {
        User user = findActiveUser(userPublicId);
        Trip trip = findActiveTripForUpdate(tripId);
        TripMember currentMember = findActiveMember(trip, user);

        LocalDate today = LocalDate.now(SEOUL_ZONE);
        if (!today.isBefore(trip.getStartDate())) {
            throw new BusinessException(ErrorCode.TRIP_LEAVE_NOT_ALLOWED);
        }

        LocalDateTime leftAt = LocalDateTime.now(SEOUL_ZONE);
        // leaveTrip은 위에서 이미 여행 시작 전임을 확인했으므로 자동 삭제 규칙이 항상 적용된다.
        return performLeave(trip, currentMember, leftAt, true);
    }

    @Override
    @Transactional
    public void leaveAllTripsForWithdrawal(
            User user,
            LocalDateTime leftAt
    ) {
        List<TripMember> memberships = tripMemberRepository
                .findByUserAndActiveSlotAndLeftAtIsNull(user, (byte) 1);
        LocalDate today = LocalDate.now(SEOUL_ZONE);

        for (TripMember member : memberships) {
            Trip trip = tripRepository
                    .findByIdForUpdate(member.getTrip().getId())
                    .orElse(null);
            if (trip == null || trip.getDeletedAt() != null) {
                continue;
            }

            // 탈퇴는 날짜와 무관하게 항상 처리하되, 이미 시작했거나 끝난 여행이면 자동 삭제
            // 규칙은 건너뛰어 다른 멤버의 완료된 여행 기록이 사라지지 않게 한다.
            boolean allowAutoDelete = today.isBefore(trip.getStartDate());
            performLeave(trip, member, leftAt, allowAutoDelete);
        }
    }

    /**
     * 나가기(직접 나가기·회원 탈퇴 공통)를 처리한다.
     * allowAutoDelete가 true이고 나간 뒤 활성 멤버가 1명 이하로 남으면(한 번이라도 2명
     * 이상이었던 방 포함) 여행방을 소프트 삭제하고 남은 멤버십도 종료하며, 후보·확정
     * 일정을 함께 정리한다.
     * allowAutoDelete가 false면(탈퇴 시점에 여행이 이미 시작했거나 끝난 경우) 방과 기록을
     * 그대로 보존한다: 멤버십 자체는 종료 처리하지 않고, 방장이었다면 host 슬롯만 내려놓고
     * 남은 멤버가 있으면 그 멤버에게 위임한다. 탈퇴한 사용자는 User.deletedAt을 기준으로
     * 화면에서 "탈퇴한 사용자"로 표시된다(getTripDetail 참고).
     * allowAutoDelete가 true이고 2명 이상 남았는데 방장이 나갔다면, 가장 먼저 참가한
     * 멤버에게 방장을 이전한다.
     */
    private TripLeaveResponse performLeave(
            Trip trip,
            TripMember currentMember,
            LocalDateTime leftAt,
            boolean allowAutoDelete
    ) {
        List<TripMember> remainingMembers = tripMemberRepository
                .findActiveMembersByTrip(trip)
                .stream()
                .filter(member -> member != currentMember)
                .toList();

        if (allowAutoDelete && remainingMembers.size() <= 1) {
            currentMember.leave(leftAt);
            remainingMembers.forEach(member -> member.leave(leftAt));
            trip.delete(leftAt);
            schedulePersistenceService.deleteAllForTrip(trip.getId());
            return new TripLeaveResponse(true, null);
        }

        if (!allowAutoDelete) {
            if (currentMember.getRole() == TripMemberRole.HOST
                    && !remainingMembers.isEmpty()) {
                currentMember.demoteFromHost();
                tripMemberRepository.flush();
                // findActiveMembersByTrip은 joinedAt 오름차순이라, 첫 원소가 가장 먼저 참가한 멤버다.
                TripMember nextHost = remainingMembers.get(0);
                nextHost.promoteToHost();
                return new TripLeaveResponse(false, nextHost.getId().toString());
            }
            return new TripLeaveResponse(false, null);
        }

        currentMember.leave(leftAt);

        if (currentMember.getRole() == TripMemberRole.HOST) {
            // findActiveMembersByTrip은 joinedAt 오름차순이라, 첫 원소가 가장 먼저 참가한 멤버다.
            TripMember nextHost = remainingMembers.get(0);
            tripMemberRepository.flush();
            nextHost.promoteToHost();
            return new TripLeaveResponse(false, nextHost.getId().toString());
        }

        return new TripLeaveResponse(false, null);
    }

    @Override
    @Transactional
    public TripCreateResponse getInvitation(
            String userPublicId,
            Long tripId
    ) {
        User user = findActiveUser(userPublicId);
        Trip trip = findActiveTripForUpdate(tripId);
        TripMember currentMember = findActiveMember(trip, user);

        if (currentMember.getRole() != TripMemberRole.HOST) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        String invitationToken = generateInvitationToken(trip.getId());
        String invitationTokenHash = tokenHasher.sha256(invitationToken);

        if (tripInvitationRepository.findByTokenHash(invitationTokenHash).isEmpty()) {
            tripInvitationRepository.save(
                    new TripInvitation(trip, invitationTokenHash)
            );
        }

        return new TripCreateResponse(
                trip.getId().toString(),
                invitationToken
        );
    }

    private String generateInvitationToken(Long tripId) {
        return tokenHasher
                .sha256(authProperties.jwt().secretBase64()
                        + ":trip-invitation:" + tripId)
                .substring(0, 43);
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

    private TripDetailResponse.Member toMemberResponse(TripMember member) {
        boolean withdrawn = member.getUser().getDeletedAt() != null;
        return new TripDetailResponse.Member(
                member.getId().toString(),
                withdrawn ? null : member.getUser().getPublicId(),
                withdrawn ? WITHDRAWN_USER_NAME : member.getUser().getUsername(),
                imageProperties.defaultProfileUrl().toString(),
                member.getRole()
        );
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

    private TripMember findActiveMember(Trip trip, User user) {
        return tripMemberRepository
                .findByTripAndUserAndLeftAtIsNull(trip, user)
                .filter(member -> member.getActiveSlot() != null
                        && member.getActiveSlot() == 1)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_MEMBER_REQUIRED
                ));
    }

    private Trip findActiveTripForUpdate(Long tripId) {
        Trip trip = tripRepository.findByIdForUpdate(tripId)
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

    private Region findRegion(Long regionId) {
        return regionRepository
                .findById(regionId)
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
