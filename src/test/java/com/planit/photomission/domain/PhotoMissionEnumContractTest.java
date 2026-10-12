package com.planit.photomission.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhotoMissionEnumContractTest {

    @DisplayName("미션의 개인·단체 범위를 정의한다")
    @Test
    void definesMissionScopes() {
        assertThat(MissionScope.values())
                .containsExactly(
                        MissionScope.PERSONAL,
                        MissionScope.GROUP
                );
    }

    @DisplayName("미션 완료 상태와 완료 방식을 정의한다")
    @Test
    void definesMissionCompletionTypes() {
        assertThat(MissionCompletionStatus.values())
                .containsExactly(
                        MissionCompletionStatus.PENDING,
                        MissionCompletionStatus.COMPLETED
                );
        assertThat(MissionCompletionMethod.values())
                .containsExactly(
                        MissionCompletionMethod.AI,
                        MissionCompletionMethod.MANUAL
                );
    }

    @DisplayName("AI 호출 실행 상태와 사진 판정 결과를 분리한다")
    @Test
    void definesPhotoEvaluationStatuses() {
        assertThat(PhotoEvaluationExecutionStatus.values())
                .containsExactly(
                        PhotoEvaluationExecutionStatus.RUNNING,
                        PhotoEvaluationExecutionStatus.SUCCEEDED,
                        PhotoEvaluationExecutionStatus.FAILED
                );
        assertThat(PhotoEvaluationResult.values())
                .containsExactly(
                        PhotoEvaluationResult.SUCCESS,
                        PhotoEvaluationResult.RETRY,
                        PhotoEvaluationResult.FAIL
                );
    }

    @DisplayName("AI 위치 불일치 사유를 정의한다")
    @Test
    void definesPhotoEvaluationReason() {
        assertThat(PhotoEvaluationReason.values())
                .containsExactly(PhotoEvaluationReason.LOCATION_MISMATCH);
    }
}
