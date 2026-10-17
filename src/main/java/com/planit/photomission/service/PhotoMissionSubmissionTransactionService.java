package com.planit.photomission.service;

import com.planit.domain.ImageFile;
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
import com.planit.repository.ImageFileRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PhotoMissionSubmissionTransactionService {

    private final MissionRepository missionRepository;
    private final MissionParticipationRepository participationRepository;
    private final MissionGroupStateRepository groupStateRepository;
    private final MissionPhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final TripMemberRepository tripMemberRepository;
    private final ImageFileRepository imageFileRepository;

    public PhotoMissionSubmissionTransactionService(
            MissionRepository missionRepository,
            MissionParticipationRepository participationRepository,
            MissionGroupStateRepository groupStateRepository,
            MissionPhotoRepository photoRepository,
            UserRepository userRepository,
            TripMemberRepository tripMemberRepository,
            ImageFileRepository imageFileRepository
    ) {
        this.missionRepository = missionRepository;
        this.participationRepository = participationRepository;
        this.groupStateRepository = groupStateRepository;
        this.photoRepository = photoRepository;
        this.userRepository = userRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.imageFileRepository = imageFileRepository;
    }

    @Transactional
    public Long save(
            String userPublicId,
            Long missionId,
            UUID idempotencyKey,
            String imageKey,
            String originalFilename,
            String mimeType,
            int sizeBytes,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        SubmissionContext context = loadSubmissionContext(
                userPublicId,
                missionId,
                idempotencyKey
        );
        Mission mission = context.mission();
        TripMember member = context.member();
        LocalDateTime now = context.now();
        ImageFile imageFile = saveImageFile(
                imageKey, originalFilename, mimeType, sizeBytes
        );
        MissionPhoto photo;
        if (mission.isPersonal()) {
            MissionParticipation participation = participationRepository
                    .findByMissionIdAndTripMemberId(missionId, member.getId())
                    .filter(this::canSubmitPersonalPhoto)
                    .orElseThrow(this::submissionNotAllowed);
            if (participation.getStatus()
                    == MissionCompletionStatus.COMPLETED) {
                photo = MissionPhoto.personalCandidate(
                        participation,
                        imageFile,
                        idempotencyKey,
                        latitude,
                        longitude,
                        now
                );
            } else {
                photoRepository.findActiveByParticipationId(
                                participation.getId()
                        )
                        .ifPresent(activePhoto -> {
                            activePhoto.replace(now);
                            photoRepository.flush();
                        });
                photo = MissionPhoto.personal(
                        participation,
                        imageFile,
                        idempotencyKey,
                        latitude,
                        longitude,
                        now
                );
            }
        } else {
            groupStateRepository.findByMissionId(missionId)
                    .filter(MissionGroupState::canSubmitPhoto)
                    .orElseThrow(this::submissionNotAllowed);
            photo = MissionPhoto.group(
                    mission,
                    member,
                    imageFile,
                    idempotencyKey,
                    latitude,
                    longitude,
                    now
            );
        }
        return photoRepository.save(photo).getId();
    }

    @Transactional
    public Long saveAdditionalGroupPhoto(
            String userPublicId,
            Long missionId,
            UUID idempotencyKey,
            String imageKey,
            String originalFilename,
            String mimeType,
            int sizeBytes,
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        SubmissionContext context = loadSubmissionContext(
                userPublicId,
                missionId,
                idempotencyKey
        );
        Mission mission = context.mission();
        if (!mission.isGroup()) {
            throw submissionNotAllowed();
        }
        MissionGroupState state = groupStateRepository
                .findByMissionId(missionId)
                .orElseThrow(this::submissionNotAllowed);
        if (state.getStatus() != MissionCompletionStatus.COMPLETED) {
            throw submissionNotAllowed();
        }

        ImageFile imageFile = saveImageFile(
                imageKey, originalFilename, mimeType, sizeBytes
        );
        MissionPhoto photo = MissionPhoto.group(
                mission,
                context.member(),
                imageFile,
                idempotencyKey,
                latitude,
                longitude,
                context.now()
        );
        return photoRepository.save(photo).getId();
    }

    private SubmissionContext loadSubmissionContext(
            String userPublicId,
            Long missionId,
            UUID idempotencyKey
    ) {
        rejectReusedIdempotencyKey(idempotencyKey);
        Mission mission = missionRepository.findById(missionId)
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
                .findByTripAndUserAndLeftAtIsNull(mission.getTrip(), user)
                .filter(TripMember::isActive)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TRIP_MEMBER_REQUIRED
                ));
        LocalDateTime now = LocalDateTime.now();
        if (mission.isExpired(now)) {
            throw submissionNotAllowed();
        }
        return new SubmissionContext(mission, member, now);
    }

    private ImageFile saveImageFile(
            String imageKey,
            String originalFilename,
            String mimeType,
            int sizeBytes
    ) {
        return imageFileRepository.save(ImageFile.mission(
                imageKey,
                originalFilename,
                mimeType,
                sizeBytes
        ));
    }

    private void rejectReusedIdempotencyKey(UUID idempotencyKey) {
        if (photoRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            throw new BusinessException(
                    ErrorCode.PHOTO_MISSION_IDEMPOTENCY_KEY_REUSED
            );
        }
    }

    private boolean canSubmitPersonalPhoto(
            MissionParticipation participation
    ) {
        return participation.getStatus() == MissionCompletionStatus.COMPLETED
                || participation.canSubmitPhoto();
    }

    private BusinessException submissionNotAllowed() {
        return new BusinessException(
                ErrorCode.PHOTO_MISSION_SUBMISSION_NOT_ALLOWED
        );
    }

    private record SubmissionContext(
            Mission mission,
            TripMember member,
            LocalDateTime now
    ) {
    }
}
