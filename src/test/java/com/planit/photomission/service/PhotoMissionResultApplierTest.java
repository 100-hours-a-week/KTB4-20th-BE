package com.planit.photomission.service;

import com.planit.photomission.domain.Mission;
import com.planit.photomission.domain.MissionCompletionStatus;
import com.planit.photomission.domain.MissionGroupState;
import com.planit.photomission.domain.MissionPhoto;
import com.planit.photomission.domain.PhotoEvaluation;
import com.planit.photomission.domain.PhotoEvaluationResult;
import com.planit.photomission.repository.MissionGroupStateRepository;
import com.planit.photomission.repository.MissionPhotoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PhotoMissionResultApplierTest {

    @DisplayName("이미 완료된 단체 미션의 후속 성공 결과는 대표 사진을 변경하지 않는다")
    @Test
    void ignoresGroupMissionSideEffectsAfterCompletion() {
        MissionPhotoRepository photoRepository =
                mock(MissionPhotoRepository.class);
        MissionGroupStateRepository groupStateRepository =
                mock(MissionGroupStateRepository.class);
        PhotoMissionResultApplier applier = new PhotoMissionResultApplier(
                photoRepository,
                groupStateRepository
        );
        PhotoEvaluation evaluation = mock(PhotoEvaluation.class);
        MissionPhoto photo = mock(MissionPhoto.class);
        Mission mission = mock(Mission.class);
        MissionGroupState state = mock(MissionGroupState.class);
        LocalDateTime evaluatedAt = LocalDateTime.of(2026, 11, 11, 12, 0);
        when(evaluation.getMissionPhoto()).thenReturn(photo);
        when(evaluation.getResult()).thenReturn(PhotoEvaluationResult.SUCCESS);
        when(photo.getMission()).thenReturn(mission);
        when(mission.isPersonal()).thenReturn(false);
        when(mission.getId()).thenReturn(31L);
        when(groupStateRepository.findByMissionIdForUpdate(31L))
                .thenReturn(Optional.of(state));
        when(state.getStatus()).thenReturn(MissionCompletionStatus.COMPLETED);

        applier.apply(evaluation, evaluatedAt);

        verify(state, never()).completeByAi(evaluatedAt);
        verify(photo, never()).markRepresentative();
    }
}
