package com.planit.photomission.domain;

import com.planit.domain.ImageFile;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.schedule.domain.ScheduleDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MissionPhotoTest {

    @DisplayName("개인 미션 사진은 참여 상태의 멤버를 제출자로 기록한다")
    @Test
    void createsPersonalPhoto() {
        TripMember member = mock(TripMember.class);
        MissionParticipation participation = MissionParticipation.create(
                personalMission(),
                member,
                now()
        );

        MissionPhoto photo = MissionPhoto.personal(
                participation,
                mock(ImageFile.class),
                UUID.randomUUID(),
                new BigDecimal("35.8350000"),
                new BigDecimal("129.2188000"),
                now()
        );

        assertThat(photo.getMission()).isSameAs(participation.getMission());
        assertThat(photo.getParticipation()).isSameAs(participation);
        assertThat(photo.getSubmittedBy()).isSameAs(member);
        assertThat(photo.getActiveSlot()).isEqualTo((byte) 1);
    }

    @DisplayName("단체 미션 사진은 개인 참여 상태 없이 제출자를 기록한다")
    @Test
    void createsGroupPhoto() {
        Mission mission = groupMission();
        TripMember submitter = mock(TripMember.class);

        MissionPhoto photo = MissionPhoto.group(
                mission,
                submitter,
                mock(ImageFile.class),
                UUID.randomUUID(),
                null,
                null,
                now()
        );

        assertThat(photo.getMission()).isSameAs(mission);
        assertThat(photo.getParticipation()).isNull();
        assertThat(photo.getSubmittedBy()).isSameAs(submitter);
        assertThat(photo.getActiveSlot()).isNull();
    }

    @DisplayName("개인 미션은 단체 사진으로 제출할 수 없다")
    @Test
    void rejectsPersonalMissionAsGroupPhoto() {
        assertThatThrownBy(() -> MissionPhoto.group(
                personalMission(),
                mock(TripMember.class),
                mock(ImageFile.class),
                UUID.randomUUID(),
                null,
                null,
                now()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("단체 사진은 단체 미션에만 제출할 수 있습니다");
    }

    @DisplayName("사진을 삭제하면 활성·대표 슬롯을 함께 해제한다")
    @Test
    void softDeletesPhoto() {
        MissionPhoto photo = MissionPhoto.group(
                groupMission(),
                mock(TripMember.class),
                mock(ImageFile.class),
                UUID.randomUUID(),
                null,
                null,
                now()
        );
        photo.markRepresentative();
        LocalDateTime deletedAt = now().plusMinutes(1);

        photo.delete(deletedAt);

        assertThat(photo.getActiveSlot()).isNull();
        assertThat(photo.getRepresentativeSlot()).isNull();
        assertThat(photo.getDeletedAt()).isEqualTo(deletedAt);
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
