package com.planit.photomission.service;

import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.photomission.domain.MissionGroupState;
import com.planit.photomission.domain.MissionCompletionMethod;
import com.planit.photomission.domain.MissionPhoto;
import com.planit.photomission.repository.MissionGroupStateRepository;
import com.planit.photomission.repository.MissionPhotoRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PhotoMissionPhotoManagementTransactionService {

    private final MissionPhotoRepository photoRepository;
    private final MissionGroupStateRepository groupStateRepository;
    private final UserRepository userRepository;
    private final TripMemberRepository tripMemberRepository;

    public PhotoMissionPhotoManagementTransactionService(
            MissionPhotoRepository photoRepository,
            MissionGroupStateRepository groupStateRepository,
            UserRepository userRepository,
            TripMemberRepository tripMemberRepository
    ) {
        this.photoRepository = photoRepository;
        this.groupStateRepository = groupStateRepository;
        this.userRepository = userRepository;
        this.tripMemberRepository = tripMemberRepository;
    }

    @Transactional
    public String delete(
            String userPublicId,
            Long missionId,
            Long photoId
    ) {
        MissionPhoto photo = photoRepository.findById(photoId)
                .filter(found -> found.getMission().getId().equals(missionId))
                .filter(found -> found.getDeletedAt() == null)
                .filter(found -> found.getReplacedAt() == null)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND
                ));
        User user = userRepository.findByPublicIdAndDeletedAtIsNull(
                        UUID.fromString(userPublicId)
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.AUTHENTICATION_REQUIRED
                ));
        TripMember member = tripMemberRepository
                .findByTripAndUserAndLeftAtIsNull(
                        photo.getMission().getTrip(),
                        user
                )
                .filter(TripMember::isActive)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_MEMBER_REQUIRED
                ));
        if (!photo.getSubmittedBy().getId().equals(member.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        LocalDateTime now = LocalDateTime.now();
        if (photo.getMission().isExpired(now)) {
            throw new BusinessException(
                    ErrorCode.PHOTO_MISSION_SUBMISSION_NOT_ALLOWED
            );
        }

        boolean representative = photo.getRepresentativeSlot() != null;
        photo.delete(now);
        if (photo.getMission().isPersonal()) {
            photo.getParticipation().reflectPhotoDeletion(false, now);
        } else {
            reflectGroupPhotoDeletion(photo, representative, now);
        }

        return photo.getImageFile().getImageKey();
    }

    private void reflectGroupPhotoDeletion(
            MissionPhoto deletedPhoto,
            boolean representative,
            LocalDateTime now
    ) {
        Long missionId = deletedPhoto.getMission().getId();
        MissionGroupState state = groupStateRepository
                .findByMissionId(missionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_SERVER_ERROR
                ));
        if (!representative && !state.canCompleteManually()) {
            return;
        }

        MissionPhoto replacement = findReplacementPhoto(
                state,
                missionId,
                deletedPhoto.getId()
        );
        if (representative && replacement != null) {
            replacement.markRepresentative();
        }
        state.reflectPhotoDeletion(replacement != null, now);
    }

    private MissionPhoto findReplacementPhoto(
            MissionGroupState state,
            Long missionId,
            Long deletedPhotoId
    ) {
        if (state.getCompletionMethod() == MissionCompletionMethod.AI) {
            return photoRepository
                    .findSuccessfulByMissionIdExcludingPhotoId(
                            missionId,
                            deletedPhotoId
                    )
                    .stream()
                    .findFirst()
                    .orElse(null);
        }
        return photoRepository
                .findAvailableByMissionIdExcludingPhotoId(
                        missionId,
                        deletedPhotoId
                )
                .stream()
                .findFirst()
                .orElse(null);
    }
}
