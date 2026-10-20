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
import com.planit.photomission.domain.PhotoEvaluationExecutionStatus;
import com.planit.photomission.domain.PhotoEvaluationResult;
import com.planit.photomission.repository.MissionGroupStateRepository;
import com.planit.photomission.repository.MissionPhotoRepository;
import com.planit.photomission.repository.PhotoEvaluationRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class PhotoMissionVerificationTransactionService {

    private static final int MAX_TECHNICAL_ATTEMPTS = 3;

    private final MissionPhotoRepository photoRepository;
    private final PhotoEvaluationRepository evaluationRepository;
    private final MissionGroupStateRepository groupStateRepository;
    private final ImageStorage imageStorage;
    private final PhotoMissionVerifyRequestFactory requestFactory;
    private final PhotoMissionEvaluationMapper evaluationMapper;
    private final Clock clock;

    public PhotoMissionVerificationTransactionService(
            MissionPhotoRepository photoRepository,
            PhotoEvaluationRepository evaluationRepository,
            MissionGroupStateRepository groupStateRepository,
            ImageStorage imageStorage,
            PhotoMissionVerifyRequestFactory requestFactory,
            PhotoMissionEvaluationMapper evaluationMapper,
            @Qualifier("photoMissionClock") Clock clock
    ) {
        this.photoRepository = photoRepository;
        this.evaluationRepository = evaluationRepository;
        this.groupStateRepository = groupStateRepository;
        this.imageStorage = imageStorage;
        this.requestFactory = requestFactory;
        this.evaluationMapper = evaluationMapper;
        this.clock = clock;
    }

    @Transactional
    public PreparedVerification prepare(Long missionPhotoId) {
        MissionPhoto photo = findPhoto(missionPhotoId);
        validatePending(photo);

        int attemptNo = evaluationRepository
                .findTopByMissionPhotoIdOrderByAttemptNoDesc(missionPhotoId)
                .map(evaluation -> evaluation.getAttemptNo() + 1)
                .orElse(1);
        return startVerification(photo, attemptNo, LocalDateTime.now(clock));
    }

    @Transactional
    public PreparedVerification prepareRetry(
            Long evaluationId,
            LocalDateTime retriedAt
    ) {
        PhotoEvaluation previous = findEvaluation(evaluationId);
        if (previous.getExecutionStatus()
                != PhotoEvaluationExecutionStatus.RETRY_PENDING
                || previous.getAttemptNo() >= MAX_TECHNICAL_ATTEMPTS) {
            throw new RetryUnavailableException(
                    "재시도 가능한 AI 판정이 아닙니다"
            );
        }
        if (previous.getRetryAfterAt().isAfter(retriedAt)) {
            throw new IllegalStateException(
                    "AI 판정 재시도 예정 시각이 아직 되지 않았습니다"
            );
        }
        MissionPhoto photo = findRetryPhoto(previous.getMissionPhoto().getId());
        try {
            validatePending(photo);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new RetryUnavailableException(
                    exception.getMessage(),
                    exception
            );
        }
        previous.markRetryStarted(retriedAt);
        return startVerification(
                photo,
                previous.getAttemptNo() + 1,
                retriedAt
        );
    }

    private PreparedVerification startVerification(
            MissionPhoto photo,
            int attemptNo,
            LocalDateTime requestedAt
    ) {
        PhotoEvaluation evaluation = evaluationRepository.save(
                PhotoEvaluation.start(photo, attemptNo, requestedAt)
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
        LocalDateTime evaluatedAt = LocalDateTime.now(clock);
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
                LocalDateTime.now(clock)
        );
    }

    @Transactional
    public void waitForRetry(
            Long evaluationId,
            String errorCode,
            LocalDateTime retryAfterAt
    ) {
        PhotoEvaluation evaluation = findEvaluation(evaluationId);
        if (evaluation.getAttemptNo() >= MAX_TECHNICAL_ATTEMPTS) {
            evaluation.failExecution(errorCode, LocalDateTime.now(clock));
            return;
        }
        evaluation.waitForRetry(errorCode, retryAfterAt);
    }

    @Transactional
    public void discardRetry(
            Long evaluationId,
            LocalDateTime discardedAt
    ) {
        PhotoEvaluation evaluation = findEvaluation(evaluationId);
        if (evaluation.getExecutionStatus()
                == PhotoEvaluationExecutionStatus.RETRY_PENDING) {
            evaluation.markRetryStarted(discardedAt);
        }
    }

    @Transactional
    public void recoverStaleRunning(
            Long evaluationId,
            LocalDateTime staleBefore,
            LocalDateTime recoveredAt
    ) {
        PhotoEvaluation evaluation = findEvaluation(evaluationId);
        if (evaluation.getExecutionStatus()
                != PhotoEvaluationExecutionStatus.RUNNING
                || evaluation.getRequestedAt().isAfter(staleBefore)) {
            return;
        }
        if (evaluation.getAttemptNo() >= MAX_TECHNICAL_ATTEMPTS) {
            evaluation.failExecution("STALE_RUNNING", recoveredAt);
            return;
        }
        evaluation.waitForRetry("STALE_RUNNING", recoveredAt);
    }

    private MissionPhoto findPhoto(Long missionPhotoId) {
        return photoRepository.findById(missionPhotoId)
                .filter(photo -> photo.getDeletedAt() == null)
                .filter(photo -> photo.getReplacedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException(
                        "판정할 포토미션 사진을 찾을 수 없습니다"
                ));
    }

    private MissionPhoto findRetryPhoto(Long missionPhotoId) {
        try {
            return findPhoto(missionPhotoId);
        } catch (IllegalArgumentException exception) {
            throw new RetryUnavailableException(
                    exception.getMessage(),
                    exception
            );
        }
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

    public static final class RetryUnavailableException
            extends IllegalStateException {

        public RetryUnavailableException(String message) {
            super(message);
        }

        public RetryUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
