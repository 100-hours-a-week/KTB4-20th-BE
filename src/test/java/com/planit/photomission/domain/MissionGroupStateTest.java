package com.planit.photomission.domain;

import com.planit.domain.Trip;
import com.planit.schedule.domain.ScheduleDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MissionGroupStateTest {

    @DisplayName("단체 미션은 하나의 공유 대기 상태로 시작한다")
    @Test
    void createsPendingGroupState() {
        Mission mission = groupMission();

        MissionGroupState state = MissionGroupState.create(mission, now());

        assertThat(state.getMission()).isSameAs(mission);
        assertThat(state.getStatus())
                .isEqualTo(MissionCompletionStatus.PENDING);
        assertThat(state.getRetryCount()).isZero();
    }

    @DisplayName("개인 미션에는 단체 공유 상태를 만들 수 없다")
    @Test
    void rejectsPersonalMission() {
        assertThatThrownBy(() ->
                MissionGroupState.create(personalMission(), now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("단체 미션만 공유 상태를 가질 수 있습니다");
    }

    @DisplayName("단체 미션의 판정 실패 횟수를 공유 상태에 누적한다")
    @Test
    void recordsSharedUnsuccessfulEvaluation() {
        MissionGroupState state =
                MissionGroupState.create(groupMission(), now());

        state.recordUnsuccessfulEvaluation(now().plusMinutes(1));

        assertThat(state.getRetryCount()).isEqualTo(1);
    }

    @DisplayName("한 멤버의 AI 성공으로 단체 공유 상태를 완료한다")
    @Test
    void completesSharedStateByAi() {
        MissionGroupState state =
                MissionGroupState.create(groupMission(), now());
        LocalDateTime completedAt = now().plusMinutes(1);

        state.completeByAi(completedAt);

        assertThat(state.getStatus())
                .isEqualTo(MissionCompletionStatus.COMPLETED);
        assertThat(state.getCompletionMethod())
                .isEqualTo(MissionCompletionMethod.AI);
        assertThat(state.getCompletedAt()).isEqualTo(completedAt);
    }

    @DisplayName("남은 성공 사진이 없을 때만 AI 완료 상태를 되돌린다")
    @Test
    void reopensOnlyAfterDeletingLastSuccessPhoto() {
        MissionGroupState state =
                MissionGroupState.create(groupMission(), now());
        state.completeByAi(now());

        state.reflectPhotoDeletion(true, now().plusMinutes(1));
        assertThat(state.getStatus())
                .isEqualTo(MissionCompletionStatus.COMPLETED);

        state.reflectPhotoDeletion(false, now().plusMinutes(2));
        assertThat(state.getStatus())
                .isEqualTo(MissionCompletionStatus.PENDING);
        assertThat(state.getCompletionMethod()).isNull();
        assertThat(state.getCompletedAt()).isNull();
    }

    @DisplayName("직접 완료 상태는 사진 삭제로 되돌리지 않는다")
    @Test
    void keepsManualCompletionAfterPhotoDeletion() {
        MissionGroupState state =
                MissionGroupState.create(groupMission(), now());
        state.recordUnsuccessfulEvaluation(now());
        state.recordUnsuccessfulEvaluation(now());
        state.recordUnsuccessfulEvaluation(now());
        state.completeManually(now());

        state.reflectPhotoDeletion(false, now().plusMinutes(1));

        assertThat(state.getStatus())
                .isEqualTo(MissionCompletionStatus.COMPLETED);
        assertThat(state.getCompletionMethod())
                .isEqualTo(MissionCompletionMethod.MANUAL);
    }

    private Mission personalMission() {
        return mission(MissionScope.PERSONAL);
    }

    private Mission groupMission() {
        return mission(MissionScope.GROUP);
    }

    private Mission mission(MissionScope scope) {
        Trip trip = mock(Trip.class);
        when(trip.getEndDate()).thenReturn(LocalDate.of(2026, 10, 8));
        return Mission.create(
                10L,
                trip,
                mock(ScheduleDay.class),
                1,
                scope,
                "포토 미션",
                "미션 설명",
                "{}",
                now()
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 10, 8, 15, 0);
    }
}
