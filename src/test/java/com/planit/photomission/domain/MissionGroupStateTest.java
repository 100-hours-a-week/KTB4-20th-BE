package com.planit.photomission.domain;

import com.planit.domain.Trip;
import com.planit.schedule.domain.Schedule;
import com.planit.schedule.domain.ScheduleDay;
import com.planit.schedule.domain.ScheduleVisit;
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

    @DisplayName("판정 실패 3회 이후에만 단체 미션을 직접 완료할 수 있다")
    @Test
    void checksManualCompletionAvailability() {
        MissionGroupState state =
                MissionGroupState.create(groupMission(), now());

        assertThat(state.canCompleteManually()).isFalse();

        state.recordUnsuccessfulEvaluation(now());
        state.recordUnsuccessfulEvaluation(now());
        state.recordUnsuccessfulEvaluation(now());

        assertThat(state.canCompleteManually()).isTrue();
        state.completeManually(now().plusMinutes(1));
        assertThat(state.canCompleteManually()).isFalse();
    }

    @DisplayName("대기 상태이고 실패 횟수가 3회 미만일 때만 단체 사진을 제출할 수 있다")
    @Test
    void checksPhotoSubmissionAvailability() {
        MissionGroupState state =
                MissionGroupState.create(groupMission(), now());

        assertThat(state.canSubmitPhoto()).isTrue();

        state.recordUnsuccessfulEvaluation(now());
        state.recordUnsuccessfulEvaluation(now());
        state.recordUnsuccessfulEvaluation(now());

        assertThat(state.canSubmitPhoto()).isFalse();
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

    @DisplayName("직접 완료한 단체 미션의 마지막 사진을 삭제하면 실패 처리한다")
    @Test
    void failsManualCompletionAfterDeletingLastPhoto() {
        MissionGroupState state =
                MissionGroupState.create(groupMission(), now());
        state.recordUnsuccessfulEvaluation(now());
        state.recordUnsuccessfulEvaluation(now());
        state.recordUnsuccessfulEvaluation(now());
        state.completeManually(now());

        state.reflectPhotoDeletion(false, now().plusMinutes(1));

        assertThat(state.getStatus())
                .isEqualTo(MissionCompletionStatus.FAILED);
        assertThat(state.getCompletionMethod()).isNull();
        assertThat(state.getCompletedAt()).isNull();
    }

    @DisplayName("직접 완료 가능한 마지막 사진을 삭제하면 단체 미션을 실패 처리한다")
    @Test
    void failsAfterDeletingLastManualCompletionPhoto() {
        MissionGroupState state =
                MissionGroupState.create(groupMission(), now());
        state.recordUnsuccessfulEvaluation(now());
        state.recordUnsuccessfulEvaluation(now());
        state.recordUnsuccessfulEvaluation(now());

        state.reflectPhotoDeletion(false, now().plusMinutes(1));

        assertThat(state.getStatus())
                .isEqualTo(MissionCompletionStatus.FAILED);
        assertThat(state.canCompleteManually()).isFalse();
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
        Schedule schedule = mock(Schedule.class);
        ScheduleDay scheduleDay = mock(ScheduleDay.class);
        ScheduleVisit scheduleVisit = mock(ScheduleVisit.class);
        when(scheduleVisit.getDay()).thenReturn(scheduleDay);
        when(scheduleDay.getSchedule()).thenReturn(schedule);
        when(schedule.getTrip()).thenReturn(trip);
        return Mission.create(
                scheduleVisit,
                1,
                scope,
                "포토 미션",
                "HISTORY_CULTURE",
                now()
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.of(2026, 10, 8, 15, 0);
    }
}
