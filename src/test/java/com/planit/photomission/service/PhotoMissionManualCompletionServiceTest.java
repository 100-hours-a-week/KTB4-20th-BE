package com.planit.photomission.service;

import com.planit.domain.ImageFile;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.photomission.domain.Mission;
import com.planit.photomission.domain.MissionCompletionMethod;
import com.planit.photomission.domain.MissionCompletionStatus;
import com.planit.photomission.domain.MissionGroupState;
import com.planit.photomission.domain.MissionParticipation;
import com.planit.photomission.domain.MissionPhoto;
import com.planit.photomission.domain.MissionScope;
import com.planit.photomission.repository.MissionGroupStateRepository;
import com.planit.photomission.repository.MissionParticipationRepository;
import com.planit.photomission.repository.MissionPhotoRepository;
import com.planit.photomission.repository.MissionRepository;
import com.planit.repository.TripMemberRepository;
import com.planit.repository.UserRepository;
import com.planit.schedule.domain.Schedule;
import com.planit.schedule.domain.ScheduleDay;
import com.planit.schedule.domain.ScheduleVisit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhotoMissionManualCompletionServiceTest {

    private static final UUID USER_PUBLIC_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final LocalDateTime SEOUL_NOW =
            LocalDateTime.of(2026, 10, 18, 10, 0);
    private static final Clock SEOUL_CLOCK = Clock.fixed(
            Instant.parse("2026-10-18T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    private MissionRepository missionRepository;
    private MissionParticipationRepository participationRepository;
    private MissionGroupStateRepository groupStateRepository;
    private MissionPhotoRepository photoRepository;
    private UserRepository userRepository;
    private TripMemberRepository tripMemberRepository;
    private PhotoMissionManualCompletionService service;

    @BeforeEach
    void setUp() {
        missionRepository = mock(MissionRepository.class);
        participationRepository = mock(MissionParticipationRepository.class);
        groupStateRepository = mock(MissionGroupStateRepository.class);
        photoRepository = mock(MissionPhotoRepository.class);
        userRepository = mock(UserRepository.class);
        tripMemberRepository = mock(TripMemberRepository.class);
        service = new PhotoMissionManualCompletionService(
                missionRepository,
                participationRepository,
                groupStateRepository,
                photoRepository,
                userRepository,
                tripMemberRepository,
                SEOUL_CLOCK
        );
    }

    @DisplayName("개인 미션은 판정 실패 3회와 남은 사진이 있으면 직접 완료한다")
    @Test
    void completesPersonalMissionManually() {
        Fixture fixture = fixture(MissionScope.PERSONAL);
        MissionParticipation participation = participation(fixture);
        MissionPhoto photo = personalPhoto(participation);
        stubAccess(fixture);
        when(participationRepository.findByMissionIdAndTripMemberId(31L, 21L))
                .thenReturn(Optional.of(participation));
        when(photoRepository.findActiveByParticipationId(61L))
                .thenReturn(Optional.of(photo));

        service.complete(USER_PUBLIC_ID.toString(), 31L);

        assertThat(participation.getStatus())
                .isEqualTo(MissionCompletionStatus.COMPLETED);
        assertThat(participation.getCompletionMethod())
                .isEqualTo(MissionCompletionMethod.MANUAL);
        assertThat(participation.getCompletedAt()).isEqualTo(SEOUL_NOW);
    }

    @DisplayName("단체 미션은 활성 멤버가 직접 완료하면 가장 오래된 사진을 대표로 지정한다")
    @Test
    void completesGroupMissionWithOldestPhoto() {
        Fixture fixture = fixture(MissionScope.GROUP);
        MissionGroupState state = MissionGroupState.create(
                fixture.mission(), now().minusHours(1)
        );
        failThreeTimes(state);
        MissionPhoto oldest = groupPhoto(fixture);
        stubAccess(fixture);
        when(groupStateRepository.findByMissionId(31L))
                .thenReturn(Optional.of(state));
        when(photoRepository
                .findFirstByMissionIdAndDeletedAtIsNullAndReplacedAtIsNullOrderBySubmittedAtAscIdAsc(
                        31L
                ))
                .thenReturn(Optional.of(oldest));

        service.complete(USER_PUBLIC_ID.toString(), 31L);

        assertThat(state.getStatus())
                .isEqualTo(MissionCompletionStatus.COMPLETED);
        assertThat(state.getCompletionMethod())
                .isEqualTo(MissionCompletionMethod.MANUAL);
        assertThat(state.getCompletedAt()).isEqualTo(SEOUL_NOW);
        assertThat(oldest.getRepresentativeSlot()).isEqualTo((byte) 1);
    }

    @DisplayName("남은 사진이 없으면 직접 완료할 수 없다")
    @Test
    void rejectsManualCompletionWithoutPhoto() {
        Fixture fixture = fixture(MissionScope.GROUP);
        MissionGroupState state = MissionGroupState.create(
                fixture.mission(), now().minusHours(1)
        );
        failThreeTimes(state);
        stubAccess(fixture);
        when(groupStateRepository.findByMissionId(31L))
                .thenReturn(Optional.of(state));
        when(photoRepository
                .findFirstByMissionIdAndDeletedAtIsNullAndReplacedAtIsNullOrderBySubmittedAtAscIdAsc(
                        31L
                ))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.complete(
                USER_PUBLIC_ID.toString(), 31L
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                ErrorCode.PHOTO_MISSION_MANUAL_COMPLETION_NOT_ALLOWED
                        )
        );
    }

    @DisplayName("판정 실패가 3회 미만이면 사진이 있어도 직접 완료할 수 없다")
    @Test
    void rejectsManualCompletionBeforeThreeFailures() {
        Fixture fixture = fixture(MissionScope.PERSONAL);
        MissionParticipation participation = MissionParticipation.create(
                fixture.mission(), fixture.member(), now().minusHours(1)
        );
        ReflectionTestUtils.setField(participation, "id", 61L);
        participation.recordUnsuccessfulEvaluation(now());
        participation.recordUnsuccessfulEvaluation(now());
        stubAccess(fixture);
        when(participationRepository.findByMissionIdAndTripMemberId(31L, 21L))
                .thenReturn(Optional.of(participation));

        assertThatThrownBy(() -> service.complete(
                USER_PUBLIC_ID.toString(), 31L
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                ErrorCode.PHOTO_MISSION_MANUAL_COMPLETION_NOT_ALLOWED
                        )
        );
    }

    private MissionParticipation participation(Fixture fixture) {
        MissionParticipation participation = MissionParticipation.create(
                fixture.mission(), fixture.member(), now().minusHours(1)
        );
        ReflectionTestUtils.setField(participation, "id", 61L);
        participation.recordUnsuccessfulEvaluation(now());
        participation.recordUnsuccessfulEvaluation(now());
        participation.recordUnsuccessfulEvaluation(now());
        return participation;
    }

    private void failThreeTimes(MissionGroupState state) {
        state.recordUnsuccessfulEvaluation(now());
        state.recordUnsuccessfulEvaluation(now());
        state.recordUnsuccessfulEvaluation(now());
    }

    private MissionPhoto personalPhoto(MissionParticipation participation) {
        return MissionPhoto.personal(
                participation,
                ImageFile.mission("photo.jpg", "photo.jpg", "image/jpeg", 10),
                UUID.randomUUID(),
                null,
                null,
                now()
        );
    }

    private MissionPhoto groupPhoto(Fixture fixture) {
        return MissionPhoto.group(
                fixture.mission(),
                fixture.member(),
                ImageFile.mission("photo.jpg", "photo.jpg", "image/jpeg", 10),
                UUID.randomUUID(),
                null,
                null,
                now()
        );
    }

    private void stubAccess(Fixture fixture) {
        when(missionRepository.findById(31L))
                .thenReturn(Optional.of(fixture.mission()));
        when(userRepository.findByPublicIdAndDeletedAtIsNull(USER_PUBLIC_ID))
                .thenReturn(Optional.of(fixture.user()));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                fixture.trip(), fixture.user()
        )).thenReturn(Optional.of(fixture.member()));
    }

    private Fixture fixture(MissionScope scope) {
        Trip trip = mock(Trip.class);
        when(trip.getEndDate()).thenReturn(LocalDate.now().plusDays(1));
        User user = new User(null, USER_PUBLIC_ID, "여행자");
        TripMember member = TripMember.createMember(trip, user);
        ReflectionTestUtils.setField(member, "id", 21L);
        Schedule schedule = mock(Schedule.class);
        ScheduleDay day = mock(ScheduleDay.class);
        ScheduleVisit visit = mock(ScheduleVisit.class);
        when(visit.getDay()).thenReturn(day);
        when(day.getSchedule()).thenReturn(schedule);
        when(schedule.getTrip()).thenReturn(trip);
        Mission mission = Mission.create(
                visit, 1, scope, "포토미션", null, now()
        );
        ReflectionTestUtils.setField(mission, "id", 31L);
        return new Fixture(trip, user, member, mission);
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private record Fixture(
            Trip trip,
            User user,
            TripMember member,
            Mission mission
    ) {
    }
}
