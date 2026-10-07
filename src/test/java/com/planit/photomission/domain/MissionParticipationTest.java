package com.planit.photomission.domain;

import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.schedule.domain.ScheduleDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MissionParticipationTest {

    @DisplayName("개인 미션은 멤버별 대기 상태로 시작한다")
    @Test
    void createsPendingParticipation() {
        TripMember member = mock(TripMember.class);

        MissionParticipation participation =
                MissionParticipation.create(personalMission(), member, now());

        assertThat(participation.getTripMember()).isSameAs(member);
        assertThat(participation.getStatus())
                .isEqualTo(MissionCompletionStatus.PENDING);
        assertThat(participation.getRetryCount()).isZero();
        assertThat(participation.getCompletionMethod()).isNull();
        assertThat(participation.getCompletedAt()).isNull();
    }

    @DisplayName("단체 미션에는 개인 참여 상태를 만들 수 없다")
    @Test
    void rejectsGroupMission() {
        assertThatThrownBy(() -> MissionParticipation.create(
                groupMission(),
                mock(TripMember.class),
                now()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("개인 미션만 멤버 참여 상태를 가질 수 있습니다");
    }

    @DisplayName("판정 실패 횟수는 멤버별로 독립적으로 누적된다")
    @Test
    void recordsUnsuccessfulEvaluationIndependently() {
        Mission mission = personalMission();
        MissionParticipation first = MissionParticipation.create(
                mission,
                mock(TripMember.class),
                now()
        );
        MissionParticipation second = MissionParticipation.create(
                mission,
                mock(TripMember.class),
                now()
        );

        first.recordUnsuccessfulEvaluation(now().plusMinutes(1));

        assertThat(first.getRetryCount()).isEqualTo(1);
        assertThat(second.getRetryCount()).isZero();
    }

    @DisplayName("판정 실패 3회 이후에만 직접 완료할 수 있다")
    @Test
    void completesManuallyAfterThreeUnsuccessfulEvaluations() {
        MissionParticipation participation = MissionParticipation.create(
                personalMission(),
                mock(TripMember.class),
                now()
        );

        assertThatThrownBy(() -> participation.completeManually(now()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("판정 실패 3회 이후에 직접 완료할 수 있습니다");

        participation.recordUnsuccessfulEvaluation(now());
        participation.recordUnsuccessfulEvaluation(now());
        participation.recordUnsuccessfulEvaluation(now());
        participation.completeManually(now().plusMinutes(1));

        assertThat(participation.getStatus())
                .isEqualTo(MissionCompletionStatus.COMPLETED);
        assertThat(participation.getCompletionMethod())
                .isEqualTo(MissionCompletionMethod.MANUAL);
    }

    @DisplayName("AI 성공 판정으로 개인 미션을 완료한다")
    @Test
    void completesByAi() {
        MissionParticipation participation = MissionParticipation.create(
                personalMission(),
                mock(TripMember.class),
                now()
        );
        LocalDateTime completedAt = now().plusMinutes(1);

        participation.completeByAi(completedAt);

        assertThat(participation.getStatus())
                .isEqualTo(MissionCompletionStatus.COMPLETED);
        assertThat(participation.getCompletionMethod())
                .isEqualTo(MissionCompletionMethod.AI);
        assertThat(participation.getCompletedAt()).isEqualTo(completedAt);
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
