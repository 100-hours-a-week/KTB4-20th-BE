package com.planit.photomission.service;

import com.planit.domain.ImageFile;
import com.planit.domain.Trip;
import com.planit.domain.TripMember;
import com.planit.domain.User;
import com.planit.global.error.BusinessException;
import com.planit.global.error.ErrorCode;
import com.planit.photomission.domain.Mission;
import com.planit.photomission.domain.MissionCompletionStatus;
import com.planit.photomission.domain.MissionGroupState;
import com.planit.photomission.domain.MissionParticipation;
import com.planit.photomission.domain.MissionPhoto;
import com.planit.photomission.domain.MissionScope;
import com.planit.photomission.repository.MissionGroupStateRepository;
import com.planit.photomission.repository.MissionPhotoRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhotoMissionPhotoManagementTransactionServiceTest {

    private static final UUID USER_PUBLIC_ID =
            UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final LocalDateTime SEOUL_NOW =
            LocalDateTime.of(2026, 10, 18, 10, 0);
    private static final Clock SEOUL_CLOCK = Clock.fixed(
            Instant.parse("2026-10-18T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    private MissionPhotoRepository photoRepository;
    private MissionGroupStateRepository groupStateRepository;
    private UserRepository userRepository;
    private TripMemberRepository tripMemberRepository;
    private PhotoMissionPhotoManagementTransactionService service;

    @BeforeEach
    void setUp() {
        photoRepository = mock(MissionPhotoRepository.class);
        groupStateRepository = mock(MissionGroupStateRepository.class);
        userRepository = mock(UserRepository.class);
        tripMemberRepository = mock(TripMemberRepository.class);
        service = new PhotoMissionPhotoManagementTransactionService(
                photoRepository,
                groupStateRepository,
                userRepository,
                tripMemberRepository,
                SEOUL_CLOCK
        );
    }

    @DisplayName("개인 성공 사진을 삭제하면 미션을 미완료로 되돌린다")
    @Test
    void deletesPersonalSuccessPhotoAndReopensMission() {
        Fixture fixture = fixture(MissionScope.PERSONAL, USER_PUBLIC_ID);
        MissionParticipation participation = MissionParticipation.create(
                fixture.mission(), fixture.member(), now().minusHours(1)
        );
        participation.completeByAi(now().minusMinutes(30));
        MissionPhoto photo = personalPhoto(participation, 41L, "personal.jpg");
        stubAccess(fixture, photo);

        String imageKey = service.delete(
                USER_PUBLIC_ID.toString(),
                fixture.mission().getId(),
                photo.getId()
        );

        assertThat(imageKey).isEqualTo("personal.jpg");
        assertThat(photo.getDeletedAt()).isEqualTo(SEOUL_NOW);
        assertThat(participation.getStatus())
                .isEqualTo(MissionCompletionStatus.PENDING);
    }

    @DisplayName("직접 완료 가능한 개인 미션의 마지막 사진을 삭제하면 실패 처리한다")
    @Test
    void failsPersonalMissionAfterDeletingLastManualCompletionPhoto() {
        Fixture fixture = fixture(MissionScope.PERSONAL, USER_PUBLIC_ID);
        MissionParticipation participation = MissionParticipation.create(
                fixture.mission(), fixture.member(), now().minusHours(1)
        );
        participation.recordUnsuccessfulEvaluation(now().minusMinutes(3));
        participation.recordUnsuccessfulEvaluation(now().minusMinutes(2));
        participation.recordUnsuccessfulEvaluation(now().minusMinutes(1));
        MissionPhoto photo = personalPhoto(participation, 41L, "personal.jpg");
        stubAccess(fixture, photo);

        service.delete(
                USER_PUBLIC_ID.toString(),
                fixture.mission().getId(),
                photo.getId()
        );

        assertThat(participation.getStatus())
                .isEqualTo(MissionCompletionStatus.FAILED);
    }

    @DisplayName("단체 대표 사진을 삭제하면 가장 오래된 성공 사진을 대표로 승계한다")
    @Test
    void promotesOldestSuccessfulGroupPhoto() {
        Fixture fixture = fixture(MissionScope.GROUP, USER_PUBLIC_ID);
        MissionGroupState state = MissionGroupState.create(
                fixture.mission(), now().minusHours(1)
        );
        state.completeByAi(now().minusMinutes(30));
        MissionPhoto representative = groupPhoto(
                fixture, 41L, "representative.jpg", true
        );
        MissionPhoto next = groupPhoto(fixture, 42L, "next.jpg", false);
        stubAccess(fixture, representative);
        when(groupStateRepository.findByMissionId(fixture.mission().getId()))
                .thenReturn(Optional.of(state));
        when(photoRepository.findSuccessfulByMissionIdExcludingPhotoId(
                fixture.mission().getId(), representative.getId()
        )).thenReturn(List.of(next));

        service.delete(
                USER_PUBLIC_ID.toString(),
                fixture.mission().getId(),
                representative.getId()
        );

        assertThat(next.getRepresentativeSlot()).isEqualTo((byte) 1);
        assertThat(state.getStatus())
                .isEqualTo(MissionCompletionStatus.COMPLETED);
    }

    @DisplayName("단체 대표 사진 삭제 후 성공 사진이 없으면 미션을 미완료로 되돌린다")
    @Test
    void reopensGroupMissionWithoutRemainingSuccessfulPhoto() {
        Fixture fixture = fixture(MissionScope.GROUP, USER_PUBLIC_ID);
        MissionGroupState state = MissionGroupState.create(
                fixture.mission(), now().minusHours(1)
        );
        state.completeByAi(now().minusMinutes(30));
        MissionPhoto representative = groupPhoto(
                fixture, 41L, "representative.jpg", true
        );
        stubAccess(fixture, representative);
        when(groupStateRepository.findByMissionId(fixture.mission().getId()))
                .thenReturn(Optional.of(state));
        when(photoRepository.findSuccessfulByMissionIdExcludingPhotoId(
                fixture.mission().getId(), representative.getId()
        )).thenReturn(List.of());

        service.delete(
                USER_PUBLIC_ID.toString(),
                fixture.mission().getId(),
                representative.getId()
        );

        assertThat(state.getStatus())
                .isEqualTo(MissionCompletionStatus.PENDING);
    }

    @DisplayName("직접 완료한 단체 미션의 대표 사진을 삭제하면 가장 오래된 유효 사진을 승계한다")
    @Test
    void promotesOldestAvailablePhotoAfterDeletingManualRepresentative() {
        Fixture fixture = fixture(MissionScope.GROUP, USER_PUBLIC_ID);
        MissionGroupState state = manuallyCompletedGroupState(fixture);
        MissionPhoto representative = groupPhoto(
                fixture, 41L, "representative.jpg", true
        );
        MissionPhoto next = groupPhoto(fixture, 42L, "next.jpg", false);
        stubAccess(fixture, representative);
        when(groupStateRepository.findByMissionId(fixture.mission().getId()))
                .thenReturn(Optional.of(state));
        when(photoRepository.findAvailableByMissionIdExcludingPhotoId(
                fixture.mission().getId(), representative.getId()
        )).thenReturn(List.of(next));

        service.delete(
                USER_PUBLIC_ID.toString(),
                fixture.mission().getId(),
                representative.getId()
        );

        assertThat(next.getRepresentativeSlot()).isEqualTo((byte) 1);
        assertThat(state.getStatus())
                .isEqualTo(MissionCompletionStatus.COMPLETED);
    }

    @DisplayName("직접 완료한 단체 미션의 마지막 사진을 삭제하면 실패 처리한다")
    @Test
    void failsManualGroupMissionAfterDeletingLastPhoto() {
        Fixture fixture = fixture(MissionScope.GROUP, USER_PUBLIC_ID);
        MissionGroupState state = manuallyCompletedGroupState(fixture);
        MissionPhoto representative = groupPhoto(
                fixture, 41L, "representative.jpg", true
        );
        stubAccess(fixture, representative);
        when(groupStateRepository.findByMissionId(fixture.mission().getId()))
                .thenReturn(Optional.of(state));
        when(photoRepository.findAvailableByMissionIdExcludingPhotoId(
                fixture.mission().getId(), representative.getId()
        )).thenReturn(List.of());

        service.delete(
                USER_PUBLIC_ID.toString(),
                fixture.mission().getId(),
                representative.getId()
        );

        assertThat(state.getStatus())
                .isEqualTo(MissionCompletionStatus.FAILED);
    }

    @DisplayName("직접 완료 가능한 단체 미션의 마지막 사진을 삭제하면 실패 처리한다")
    @Test
    void failsPendingGroupMissionAfterDeletingLastPhoto() {
        Fixture fixture = fixture(MissionScope.GROUP, USER_PUBLIC_ID);
        MissionGroupState state = MissionGroupState.create(
                fixture.mission(), now().minusHours(1)
        );
        state.recordUnsuccessfulEvaluation(now().minusMinutes(3));
        state.recordUnsuccessfulEvaluation(now().minusMinutes(2));
        state.recordUnsuccessfulEvaluation(now().minusMinutes(1));
        MissionPhoto photo = groupPhoto(fixture, 41L, "last.jpg", false);
        stubAccess(fixture, photo);
        when(groupStateRepository.findByMissionId(fixture.mission().getId()))
                .thenReturn(Optional.of(state));
        when(photoRepository.findAvailableByMissionIdExcludingPhotoId(
                fixture.mission().getId(), photo.getId()
        )).thenReturn(List.of());

        service.delete(
                USER_PUBLIC_ID.toString(),
                fixture.mission().getId(),
                photo.getId()
        );

        assertThat(state.getStatus())
                .isEqualTo(MissionCompletionStatus.FAILED);
    }

    @DisplayName("다른 멤버가 제출한 사진은 삭제할 수 없다")
    @Test
    void rejectsDeletingAnotherMembersPhoto() {
        Fixture owner = fixture(MissionScope.GROUP, UUID.randomUUID());
        MissionPhoto photo = groupPhoto(owner, 41L, "other.jpg", false);
        when(photoRepository.findById(41L)).thenReturn(Optional.of(photo));
        User requester = new User(null, USER_PUBLIC_ID, "요청자");
        TripMember requesterMember = TripMember.createMember(
                owner.trip(), requester
        );
        when(userRepository.findByPublicIdAndDeletedAtIsNull(USER_PUBLIC_ID))
                .thenReturn(Optional.of(requester));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                owner.trip(), requester
        )).thenReturn(Optional.of(requesterMember));

        assertThatThrownBy(() -> service.delete(
                USER_PUBLIC_ID.toString(),
                owner.mission().getId(),
                photo.getId()
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.ACCESS_DENIED)
        );
    }

    @DisplayName("여행 종료 후에는 제출한 사진을 삭제할 수 없다")
    @Test
    void rejectsPhotoDeletionAfterTripEnd() {
        Fixture fixture = fixture(MissionScope.PERSONAL, USER_PUBLIC_ID);
        MissionParticipation participation = MissionParticipation.create(
                fixture.mission(), fixture.member(), now().minusHours(1)
        );
        MissionPhoto photo = personalPhoto(participation, 41L, "photo.jpg");
        ReflectionTestUtils.setField(
                fixture.mission(),
                "expiresAt",
                now().minusSeconds(1)
        );
        stubAccess(fixture, photo);

        assertThatThrownBy(() -> service.delete(
                USER_PUBLIC_ID.toString(),
                fixture.mission().getId(),
                photo.getId()
        )).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                ErrorCode.PHOTO_MISSION_SUBMISSION_NOT_ALLOWED
                        )
        );
    }

    private void stubAccess(Fixture fixture, MissionPhoto photo) {
        when(photoRepository.findById(photo.getId()))
                .thenReturn(Optional.of(photo));
        when(userRepository.findByPublicIdAndDeletedAtIsNull(USER_PUBLIC_ID))
                .thenReturn(Optional.of(fixture.user()));
        when(tripMemberRepository.findByTripAndUserAndLeftAtIsNull(
                fixture.trip(), fixture.user()
        )).thenReturn(Optional.of(fixture.member()));
    }

    private MissionPhoto personalPhoto(
            MissionParticipation participation,
            Long id,
            String imageKey
    ) {
        MissionPhoto photo = MissionPhoto.personal(
                participation,
                ImageFile.mission(imageKey, imageKey, "image/jpeg", 10),
                UUID.randomUUID(),
                null,
                null,
                now().minusMinutes(20)
        );
        ReflectionTestUtils.setField(photo, "id", id);
        return photo;
    }

    private MissionPhoto groupPhoto(
            Fixture fixture,
            Long id,
            String imageKey,
            boolean representative
    ) {
        MissionPhoto photo = MissionPhoto.group(
                fixture.mission(),
                fixture.member(),
                ImageFile.mission(imageKey, imageKey, "image/jpeg", 10),
                UUID.randomUUID(),
                null,
                null,
                now().minusMinutes(20).plusSeconds(id)
        );
        ReflectionTestUtils.setField(photo, "id", id);
        if (representative) {
            photo.markRepresentative();
        }
        return photo;
    }

    private MissionGroupState manuallyCompletedGroupState(Fixture fixture) {
        MissionGroupState state = MissionGroupState.create(
                fixture.mission(), now().minusHours(1)
        );
        state.recordUnsuccessfulEvaluation(now().minusMinutes(3));
        state.recordUnsuccessfulEvaluation(now().minusMinutes(2));
        state.recordUnsuccessfulEvaluation(now().minusMinutes(1));
        state.completeManually(now());
        return state;
    }

    private Fixture fixture(MissionScope scope, UUID userPublicId) {
        Trip trip = mock(Trip.class);
        when(trip.getEndDate()).thenReturn(
                LocalDate.now(SEOUL_CLOCK).plusDays(1)
        );
        User user = new User(null, userPublicId, "여행자");
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
        return LocalDateTime.now(SEOUL_CLOCK);
    }

    private record Fixture(
            Trip trip,
            User user,
            TripMember member,
            Mission mission
    ) {
    }
}
