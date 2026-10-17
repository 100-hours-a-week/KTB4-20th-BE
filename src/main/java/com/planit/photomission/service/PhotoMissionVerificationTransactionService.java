package com.planit.photomission.service;

import com.planit.image.storage.ImageStorage;
import com.planit.photomission.ai.PhotoMissionEvaluationMapper;
import com.planit.photomission.ai.PhotoMissionVerifyRequest;
import com.planit.photomission.ai.PhotoMissionVerifyRequestFactory;
import com.planit.photomission.ai.PhotoMissionVerifyResponse;
import com.planit.photomission.domain.MissionCompletionStatus;
import com.planit.photomission.domain.MissionGroupState;
import com.planit.photomission.domain.MissionParticipation;
import com.planit.photomission.domain.MissionPhoto;
import com.planit.photomission.domain.PhotoEvaluation;
import com.planit.photomission.domain.PhotoEvaluationResult;
import com.planit.photomission.repository.MissionGroupStateRepository;
import com.planit.photomission.repository.MissionPhotoRepository;
import com.planit.photomission.repository.PhotoEvaluationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PhotoMissionVerificationTransactionService {

    private final MissionPhotoRepository photoRepository;
    private final PhotoEvaluationRepository evaluationRepository;
    private final MissionGroupStateRepository groupStateRepository;
    private final ImageStorage imageStorage;
    private final PhotoMissionVerifyRequestFactory requestFactory;
    private final PhotoMissionEvaluationMapper evaluationMapper;

    public PhotoMissionVerificationTransactionService(
            MissionPhotoRepository photoRepository,
            PhotoEvaluationRepository evaluationRepository,
            MissionGroupStateRepository groupStateRepository,
            ImageStorage imageStorage,
            PhotoMissionVerifyRequestFactory requestFactory,
            PhotoMissionEvaluationMapper evaluationMapper
    ) {
        this.photoRepository = photoRepository;
        this.evaluationRepository = evaluationRepository;
        this.groupStateRepository = groupStateRepository;
        this.imageStorage = imageStorage;
        this.requestFactory = requestFactory;
        this.evaluationMapper = evaluationMapper;
    }

    @Transactional
    public PreparedVerification prepare(Long missionPhotoId) {
        MissionPhoto photo = findPhoto(missionPhotoId);
        validatePending(photo);

        int attemptNo = evaluationRepository
                .findTopByMissionPhotoIdOrderByAttemptNoDesc(missionPhotoId)
                .map(evaluation -> evaluation.getAttemptNo() + 1)
                .orElse(1);
        PhotoEvaluation evaluation = evaluationRepository.save(
                PhotoEvaluation.start(photo, attemptNo, LocalDateTime.now())
        );
        String imageUrl = imageStorage.createReadUrl(
                photo.getImageFile().getImageKey()
        );

        return new PreparedVerification(
                evaluation.getId(),
                photo.getMission().getId(),
                requestFactory.create(photo, imageUrl)
        );
    }

    @Transactional
    public void complete(
            Long evaluationId,
            PhotoMissionVerifyResponse response
    ) {
        PhotoEvaluation evaluation = findEvaluation(evaluationId);
        LocalDateTime evaluatedAt = LocalDateTime.now();
        evaluationMapper.apply(evaluation, response, evaluatedAt);

        MissionPhoto photo = evaluation.getMissionPhoto();
        if (evaluation.getResult() == PhotoEvaluationResult.SUCCESS) {
            completeMission(photo, evaluatedAt);
            return;
        }
        recordUnsuccessfulEvaluation(photo, evaluatedAt);
    }

    @Transactional
    public void fail(Long evaluationId, String errorCode) {
        findEvaluation(evaluationId).failExecution(
                errorCode,
                LocalDateTime.now()
        );
    }

    @Transactional
    public void waitForRetry(
            Long evaluationId,
            String errorCode,
            LocalDateTime retryAfterAt
    ) {
        findEvaluation(evaluationId).waitForRetry(
                errorCode,
                retryAfterAt
        );
    }

    private MissionPhoto findPhoto(Long missionPhotoId) {
        return photoRepository.findById(missionPhotoId)
                .filter(photo -> photo.getDeletedAt() == null)
                .filter(photo -> photo.getReplacedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException(
                        "판정할 포토미션 사진을 찾을 수 없습니다"
                ));
    }

    private PhotoEvaluation findEvaluation(Long evaluationId) {
        return evaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "포토미션 판정 실행을 찾을 수 없습니다"
                ));
    }

    private void validatePending(MissionPhoto photo) {
        if (photo.getMission().isPersonal()) {
            MissionParticipation participation = photo.getParticipation();
            if (!canVerifyPersonalPhoto(photo, participation)) {
                throw new IllegalStateException(
                        "AI 판정이 가능한 개인 미션이 아닙니다"
                );
            }
            return;
        }

        MissionGroupState state = findGroupState(
                photo.getMission().getId()
        );
        if (state.getStatus() != MissionCompletionStatus.PENDING
                || state.getRetryCount()
                >= MissionGroupState.MAX_RETRY_COUNT) {
            throw new IllegalStateException(
                    "AI 판정이 가능한 단체 미션이 아닙니다"
            );
        }
    }

    private void completeMission(
            MissionPhoto photo,
            LocalDateTime evaluatedAt
    ) {
        if (photo.getMission().isPersonal()) {
            MissionParticipation participation = photo.getParticipation();
            if (participation.getStatus()
                    == MissionCompletionStatus.COMPLETED) {
                replaceCompletedPersonalPhoto(photo, evaluatedAt);
            } else {
                participation.completeByAi(evaluatedAt);
            }
            return;
        }

        findGroupState(photo.getMission().getId())
                .completeByAi(evaluatedAt);
        photo.markRepresentative();
    }

    private void recordUnsuccessfulEvaluation(
            MissionPhoto photo,
            LocalDateTime evaluatedAt
    ) {
        if (photo.getMission().isPersonal()) {
            if (photo.getParticipation().getStatus()
                    == MissionCompletionStatus.COMPLETED) {
                photo.replace(evaluatedAt);
            } else {
                photo.getParticipation()
                        .recordUnsuccessfulEvaluation(evaluatedAt);
            }
            return;
        }

        findGroupState(photo.getMission().getId())
                .recordUnsuccessfulEvaluation(evaluatedAt);
    }

    private boolean canVerifyPersonalPhoto(
            MissionPhoto photo,
            MissionParticipation participation
    ) {
        if (participation == null) {
            return false;
        }
        if (participation.getStatus() == MissionCompletionStatus.COMPLETED) {
            return photo.getActiveSlot() == null;
        }
        return participation.getRetryCount()
                < MissionParticipation.MAX_RETRY_COUNT;
    }

    private void replaceCompletedPersonalPhoto(
            MissionPhoto candidate,
            LocalDateTime evaluatedAt
    ) {
        MissionParticipation participation = candidate.getParticipation();
        photoRepository.findActiveByParticipationId(participation.getId())
                .ifPresent(activePhoto -> {
                    activePhoto.replace(evaluatedAt);
                    photoRepository.flush();
                });
        candidate.activate();
    }

    private MissionGroupState findGroupState(Long missionId) {
        return groupStateRepository.findByMissionId(missionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "단체 미션 상태를 찾을 수 없습니다"
                ));
    }

    public record PreparedVerification(
            Long evaluationId,
            Long missionId,
            PhotoMissionVerifyRequest request
    ) {
    }
}
