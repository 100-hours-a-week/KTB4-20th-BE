package com.planit.photomission.service;

import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.photomission.domain.Mission;
import com.planit.photomission.domain.MissionGroupState;
import com.planit.photomission.domain.MissionParticipation;
import com.planit.photomission.domain.MissionPhoto;
import com.planit.photomission.repository.MissionGroupStateRepository;
import com.planit.photomission.repository.MissionParticipationRepository;
import com.planit.photomission.repository.MissionPhotoRepository;
import com.planit.photomission.repository.MissionRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.UserRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PhotoMissionManualCompletionService {

    private final MissionRepository missionRepository;
    private final MissionParticipationRepository participationRepository;
    private final MissionGroupStateRepository groupStateRepository;
    private final MissionPhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final TripMemberRepository tripMemberRepository;
    private final Clock clock;

    public PhotoMissionManualCompletionService(
            MissionRepository missionRepository,
            MissionParticipationRepository participationRepository,
            MissionGroupStateRepository groupStateRepository,
            MissionPhotoRepository photoRepository,
            UserRepository userRepository,
            TripMemberRepository tripMemberRepository,
            @Qualifier("photoMissionClock") Clock clock
    ) {
        this.missionRepository = missionRepository;
        this.participationRepository = participationRepository;
        this.groupStateRepository = groupStateRepository;
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.clock = clock;
    }

    @Transactional
    public void complete(String userPublicId, Long missionId) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND
                ));
        TripMember member = findActiveMember(userPublicId, mission);
        LocalDateTime now = LocalDateTime.now(clock);
        if (mission.isExpired(now)) {
            throw notAllowed();
        }

        if (mission.isPersonal()) {
            completePersonalMission(missionId, member.getId(), now);
        } else {
            completeGroupMission(missionId, now);
        }
    }

    private TripMember findActiveMember(
            String userPublicId,
            Mission mission
    ) {
        User user = userRepository.findByPublicIdAndDeletedAtIsNull(
                        UUID.fromString(userPublicId)
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED
                ));
        return tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                        mission.getTrip(), user
                )
                .filter(TripMember::isActive)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_MEMBER_REQUIRED
                ));
    }

    private void completePersonalMission(
            Long missionId,
            Long memberId,
            LocalDateTime completedAt
    ) {
        MissionParticipation participation = participationRepository
                .findByMissionIdAndTripMemberId(missionId, memberId)
                .filter(MissionParticipation::canCompleteManually)
                .orElseThrow(this::notAllowed);
        photoRepository.findActiveByParticipationId(participation.getId())
                .orElseThrow(this::notAllowed);
        participation.completeManually(completedAt);
    }

    private void completeGroupMission(
            Long missionId,
            LocalDateTime completedAt
    ) {
        MissionGroupState state = groupStateRepository
                .findByMissionId(missionId)
                .filter(MissionGroupState::canCompleteManually)
                .orElseThrow(this::notAllowed);
        MissionPhoto representative = photoRepository
                .findFirstByMissionIdAndDeletedAtIsNullAndReplacedAtIsNullOrderBySubmittedAtAscIdAsc(
                        missionId
                )
                .orElseThrow(this::notAllowed);
        state.completeManually(completedAt);
        representative.markRepresentative();
    }

    private BusinessException notAllowed() {
        return new BusinessException(
                ErrorCode.PHOTO_MISSION_MANUAL_COMPLETION_NOT_ALLOWED
        );
    }
}
