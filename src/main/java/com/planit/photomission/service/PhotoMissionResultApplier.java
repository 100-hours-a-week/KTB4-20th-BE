package com.planit.photomission.service;

import com.planit.photomission.domain.MissionCompletionStatus;
import com.planit.photomission.domain.MissionGroupState;
import com.planit.photomission.domain.MissionParticipation;
import com.planit.photomission.domain.MissionPhoto;
import com.planit.photomission.domain.PhotoEvaluation;
import com.planit.photomission.domain.PhotoEvaluationResult;
import com.planit.photomission.repository.MissionGroupStateRepository;
import com.planit.photomission.repository.MissionPhotoRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class PhotoMissionResultApplier {

    private final MissionPhotoRepository photoRepository;
    private final MissionGroupStateRepository groupStateRepository;

    public PhotoMissionResultApplier(
            MissionPhotoRepository photoRepository,
            MissionGroupStateRepository groupStateRepository
    ) {
        this.photoRepository = photoRepository;
        this.groupStateRepository = groupStateRepository;
    }

    public void apply(
            PhotoEvaluation evaluation,
            LocalDateTime evaluatedAt
    ) {
        MissionPhoto photo = evaluation.getMissionPhoto();
        if (evaluation.getResult() == PhotoEvaluationResult.SUCCESS) {
            completeMission(photo, evaluatedAt);
            return;
        }
        recordUnsuccessfulEvaluation(photo, evaluatedAt);
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
}
